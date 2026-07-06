package wiki.nplus.airadar.digester

import wiki.nplus.airadar.common.Config
import wiki.nplus.airadar.common.DigestResult
import java.net.http.HttpClient

/**
 * Provider-agnostic digest interface. The pipeline only ever sees
 * [DigestResult]; swapping providers (Gemini today — ADR-007) touches nothing
 * outside this package.
 */
interface LlmClient {
    val model: String

    fun digest(source: String, title: String, url: String, text: String?): DigestResult

    fun cost(inputTokens: Int, outputTokens: Int): Double = 0.0

    companion object {
        fun fromEnv(http: HttpClient): LlmClient = when (val provider = Config.str("LLM_PROVIDER", "gemini")) {
            "gemini" -> GeminiClient(http)
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
}
