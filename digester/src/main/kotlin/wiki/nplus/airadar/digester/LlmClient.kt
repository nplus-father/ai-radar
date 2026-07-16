package wiki.nplus.airadar.digester

import wiki.nplus.airadar.common.Config
import wiki.nplus.airadar.common.DigestResult
import wiki.nplus.airadar.common.EssayResult
import wiki.nplus.airadar.common.ItemRepository
import wiki.nplus.airadar.common.SelectResult
import java.net.http.HttpClient

/**
 * Provider-agnostic LLM interface. The pipeline only ever sees the result
 * types; swapping providers (Gemini today — ADR-007) touches nothing outside
 * this package.
 *
 * Three tiers, three instances (ADR-009): [fromEnv] is the cheap per-item
 * digest model, [selectorFromEnv] the stronger once-a-day selection model,
 * [essayistFromEnv] the once-a-day essay model. Same interface, different
 * model + per-Mtok rates.
 */
interface LlmClient {
    val model: String

    fun digest(source: String, title: String, url: String, text: String?): DigestResult

    /** Relative ranking: pick at most [maxPicks] of [candidates] worth a deep commentary. */
    fun select(candidates: List<ItemRepository.SelectionCandidate>, maxPicks: Int): SelectResult

    /** The daily essay: news + library passages → book-informed commentary, or a refusal. */
    fun essay(candidate: ItemRepository.EssayCandidate, chapters: List<ChapterExcerpt>): EssayResult

    fun cost(inputTokens: Int, outputTokens: Int): Double = 0.0

    /** A chapter pulled in full for quoting, with its provenance. */
    data class ChapterExcerpt(val bookTitle: String, val chapterTitle: String, val chapterId: String, val content: String)

    companion object {
        private fun gemini(http: HttpClient, build: (HttpClient) -> GeminiClient): LlmClient =
            when (val provider = Config.str("LLM_PROVIDER", "gemini")) {
                "gemini" -> build(http)
                "fake" -> FakeLlmClient()
                else -> error("Unknown LLM_PROVIDER: $provider")
            }

        fun fromEnv(http: HttpClient): LlmClient = gemini(http) { GeminiClient(it) }

        fun selectorFromEnv(http: HttpClient): LlmClient = gemini(http) {
            GeminiClient(
                it,
                model = Config.str("SELECT_MODEL", "gemini-2.5-pro"),
                inputUsdPerMTok = Config.double("SELECT_INPUT_USD_PER_MTOK", 1.25),
                outputUsdPerMTok = Config.double("SELECT_OUTPUT_USD_PER_MTOK", 10.00),
            )
        }

        fun essayistFromEnv(http: HttpClient): LlmClient = gemini(http) {
            GeminiClient(
                it,
                model = Config.str("ESSAY_MODEL", "gemini-2.5-pro"),
                inputUsdPerMTok = Config.double("ESSAY_INPUT_USD_PER_MTOK", 1.25),
                outputUsdPerMTok = Config.double("ESSAY_OUTPUT_USD_PER_MTOK", 10.00),
            )
        }
    }
}

/** Deterministic stand-in: full pipeline runs, zero spend. Also used by tests. */
class FakeLlmClient : LlmClient {
    override val model = "fake"

    override fun digest(source: String, title: String, url: String, text: String?): DigestResult = DigestResult(
        summaryZh = "（測試摘要）$title",
        summaryEn = "(fake summary) $title",
        tagsJson = """["fake"]""",
        significanceScore = 3,
        category = "other",
        model = model,
        inputTokens = 0,
        outputTokens = 0,
    )

    override fun select(candidates: List<ItemRepository.SelectionCandidate>, maxPicks: Int): SelectResult = SelectResult(
        picks = candidates.take(maxPicks).map { SelectResult.Pick(it.item.itemId, "（測試理由）${it.item.title}") },
        model = model,
        inputTokens = 0,
        outputTokens = 0,
    )

    override fun essay(candidate: ItemRepository.EssayCandidate, chapters: List<LlmClient.ChapterExcerpt>): EssayResult = EssayResult(
        skip = false,
        skipReason = null,
        titleZh = "（測試評析）${candidate.title}",
        essayMd = "（測試評析內文）引用：${chapters.joinToString { it.chapterTitle }}",
        booksJson = """[{"book_id":"fake-book","book_title":"假書","chapter_id":"fake-book:c1","chapter_title":"假章"}]""",
        model = model,
        inputTokens = 0,
        outputTokens = 0,
    )
}
