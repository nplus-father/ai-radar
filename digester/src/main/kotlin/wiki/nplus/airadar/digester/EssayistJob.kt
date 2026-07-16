package wiki.nplus.airadar.digester

import com.rabbitmq.client.Channel
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import wiki.nplus.airadar.common.Config
import wiki.nplus.airadar.common.ItemRepository
import wiki.nplus.airadar.common.LibraryClient
import wiki.nplus.airadar.common.Rabbit
import wiki.nplus.airadar.common.RabbitTopology
import wiki.nplus.airadar.common.StageMessage
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The daily essay (news-echo Phase 2): once per UTC day after ESSAY_HOUR_UTC,
 * take the strongest-resonance uncomposed shortlist pick, pull the full text
 * of its top chapters from the library, and have ESSAY_MODEL write the
 * book-informed commentary. One LLM attempt per day; the model may refuse
 * (skip) when the passages cannot support an essay — days without an essay
 * are a legal outcome (寧缺勿濫), and a skipped pick is consumed so the same
 * dead end is not retried tomorrow.
 *
 * Runs in the digester process for the same reason as the curator (ADR-009):
 * one process spends all LLM money.
 */
class EssayistJob(
    private val repo: ItemRepository,
    private val essayist: LlmClient,
    private val library: LibraryClient,
    private val channel: Channel,
    private val registry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(EssayistJob::class.java)
    private val essayHourUtc = Config.int("ESSAY_HOUR_UTC", 22)
    private val ttlDays = Config.int("SHORTLIST_TTL_DAYS", 7)
    private val maxChapters = Config.int("ESSAY_MAX_CHAPTERS", 2)
    private val dailyBudgetUsd = Config.double("DAILY_LLM_BUDGET_USD", 0.50)
    private fun outcome(name: String) = registry.counter("airadar_essay_runs_total", "outcome", name)

    fun runIfDue(now: Instant) {
        val utcNow = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        if (utcNow.hour < essayHourUtc) return
        val day = utcNow.toLocalDate()
        if (repo.essayExistsForDay(day)) return

        val candidate = repo.essayCandidates(ttlDays).firstOrNull()
        if (candidate == null) {
            // No pending pick: not an error, the pool refills as news resonates.
            return
        }

        val spent = repo.costSpentToday()
        if (spent >= dailyBudgetUsd) {
            outcome("budget_skipped").increment()
            log.warn("essay {}: skipped, ${"$%.4f".format(spent)} of ${"$%.2f".format(dailyBudgetUsd)} spent", day)
            return
        }

        val chapters = topChapters(candidate.passagesJson)
        val result = essayist.essay(candidate, chapters)
        val usd = essayist.cost(result.inputTokens, result.outputTokens)
        repo.recordUsage(candidate.itemId, "ESSAY", result.model, result.inputTokens, result.outputTokens, usd)

        if (result.skip) {
            // Consume the pick: retrying the same pairing tomorrow would burn
            // the same money on the same dead end.
            repo.markComposed(candidate.itemId)
            outcome("skipped").increment()
            log.info("essay {}: model declined item {} ({}): {}", day, candidate.itemId, candidate.title, result.skipReason)
            return
        }

        repo.saveEssay(
            day = day,
            itemId = candidate.itemId,
            title = result.titleZh ?: candidate.title,
            essayMd = result.essayMd ?: error("essay without body for item ${candidate.itemId}"),
            booksJson = result.booksJson,
            model = result.model,
        )
        repo.markComposed(candidate.itemId)
        Rabbit.publish(channel, "", RabbitTopology.PUBLISH_QUEUE, StageMessage(candidate.itemId, kind = "essay").encode())
        outcome("composed").increment()
        log.info("essay {}: composed from item {} ({}), {} book(s)", day, candidate.itemId, candidate.title, Json.parseToJsonElement(result.booksJson).jsonArray.size)
    }

    /** Fetch full text for the strongest distinct chapters among the match passages. */
    private fun topChapters(passagesJson: String): List<LlmClient.ChapterExcerpt> =
        Json.parseToJsonElement(passagesJson).jsonArray
            .map { it.jsonObject }
            .distinctBy { it["chapter_id"]?.jsonPrimitive?.content }
            .take(maxChapters)
            .mapNotNull { p ->
                val chapterId = p["chapter_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val content = library.chapter(chapterId) ?: return@mapNotNull null
                LlmClient.ChapterExcerpt(
                    bookTitle = p["book_title"]?.jsonPrimitive?.content ?: "",
                    chapterTitle = p["chapter_title"]?.jsonPrimitive?.content ?: "",
                    chapterId = chapterId,
                    content = content,
                )
            }
}
