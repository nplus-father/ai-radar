package wiki.nplus.airadar.digester

import wiki.nplus.airadar.common.Config
import wiki.nplus.airadar.common.DigestResult
import wiki.nplus.airadar.common.ItemRepository
import wiki.nplus.airadar.common.SelectResult
import java.net.http.HttpClient

/**
 * Provider-agnostic LLM interface. The pipeline only ever sees [DigestResult]
 * and [SelectResult]; swapping providers (Gemini today — ADR-007) touches
 * nothing outside this package.
 *
 * Two tiers, two instances (ADR-009): [fromEnv] is the cheap per-item digest
 * model, [selectorFromEnv] the stronger once-a-day selection model. Same
 * interface, different model + per-Mtok rates.
 */
interface LlmClient {
    val model: String

    fun digest(source: String, title: String, url: String, text: String?): DigestResult

    /** Relative ranking: pick at most [maxPicks] of [candidates] worth a deep commentary. */
    fun select(candidates: List<ItemRepository.DigestedItem>, maxPicks: Int): SelectResult

    fun cost(inputTokens: Int, outputTokens: Int): Double = 0.0

    companion object {
        fun fromEnv(http: HttpClient): LlmClient = when (val provider = Config.str("LLM_PROVIDER", "gemini")) {
            "gemini" -> GeminiClient(http)
            "fake" -> FakeLlmClient()
            else -> error("Unknown LLM_PROVIDER: $provider")
        }

        fun selectorFromEnv(http: HttpClient): LlmClient = when (val provider = Config.str("LLM_PROVIDER", "gemini")) {
            "gemini" -> GeminiClient(
                http,
                model = Config.str("SELECT_MODEL", "gemini-2.5-pro"),
                inputUsdPerMTok = Config.double("SELECT_INPUT_USD_PER_MTOK", 1.25),
                outputUsdPerMTok = Config.double("SELECT_OUTPUT_USD_PER_MTOK", 10.00),
            )
            "fake" -> FakeLlmClient()
            else -> error("Unknown LLM_PROVIDER: $provider")
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

    override fun select(candidates: List<ItemRepository.DigestedItem>, maxPicks: Int): SelectResult = SelectResult(
        picks = candidates.take(maxPicks).map { SelectResult.Pick(it.itemId, "（測試理由）${it.title}") },
        model = model,
        inputTokens = 0,
        outputTokens = 0,
    )
}
