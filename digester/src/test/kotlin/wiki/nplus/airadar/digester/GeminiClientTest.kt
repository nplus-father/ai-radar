package wiki.nplus.airadar.digester

import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeminiClientTest {

    // parseResponse/cost are pure; the API key is resolved lazily and never
    // touched by these tests.
    private fun client(): GeminiClient = GeminiClient(HttpClient.newHttpClient())

    private val sampleResponse = """
        {
          "candidates": [{
            "content": {"parts": [{"text": "{\"summary_zh\": \"中文摘要\", \"summary_en\": \"English summary\", \"tags\": [\"llm\", \"release\"], \"significance_score\": 4, \"category\": \"product\"}"}]}
          }],
          "usageMetadata": {"promptTokenCount": 1200, "candidatesTokenCount": 150}
        }
    """.trimIndent()

    @Test
    fun `parses a well-formed response`() {
        val result = client().parseResponse(sampleResponse, "gemini-test")
        assertEquals("中文摘要", result.summaryZh)
        assertEquals("English summary", result.summaryEn)
        assertEquals("""["llm","release"]""", result.tagsJson)
        assertEquals(4, result.significanceScore)
        assertEquals("product", result.category)
        assertEquals(1200, result.inputTokens)
        assertEquals(150, result.outputTokens)
    }

    @Test
    fun `score outside 1-5 is clamped`() {
        val body = sampleResponse.replace("\\\"significance_score\\\": 4", "\\\"significance_score\\\": 9")
        assertEquals(5, client().parseResponse(body, "gemini-test").significanceScore)
    }

    @Test
    fun `missing required field fails fast (goes to DLQ, not retry)`() {
        val body = sampleResponse.replace("summary_zh", "summary_xx")
        assertFailsWith<IllegalStateException> { client().parseResponse(body, "gemini-test") }
    }

    @Test
    fun `cost uses per-mtok rates`() {
        val cost = client().cost(1_000_000, 1_000_000)
        assertEquals(0.30 + 2.50, cost, 1e-9)
    }

    private val sampleSelection = """
        {
          "candidates": [{
            "content": {"parts": [{"text": "{\"picks\": [{\"id\": 42, \"reason\": \"值得深評\"}, {\"id\": 7, \"reason\": \"框架契合\"}]}"}]}
          }],
          "usageMetadata": {"promptTokenCount": 3000, "candidatesTokenCount": 200}
        }
    """.trimIndent()

    @Test
    fun `parses a well-formed selection`() {
        val result = client().parseSelection(sampleSelection, "gemini-test")
        assertEquals(listOf(42L, 7L), result.picks.map { it.itemId })
        assertEquals("值得深評", result.picks[0].reason)
        assertEquals(3000, result.inputTokens)
        assertEquals(200, result.outputTokens)
    }

    @Test
    fun `empty picks is a valid selection`() {
        val body = sampleSelection.replace(
            "[{\\\"id\\\": 42, \\\"reason\\\": \\\"值得深評\\\"}, {\\\"id\\\": 7, \\\"reason\\\": \\\"框架契合\\\"}]",
            "[]",
        )
        assertEquals(0, client().parseSelection(body, "gemini-test").picks.size)
    }

    @Test
    fun `selection missing picks fails fast`() {
        val body = sampleSelection.replace("picks", "choices")
        assertFailsWith<IllegalStateException> { client().parseSelection(body, "gemini-test") }
    }
}
