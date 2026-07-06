package wiki.nplus.airadar.digester

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import wiki.nplus.airadar.common.Config
import wiki.nplus.airadar.common.DigestResult
import wiki.nplus.airadar.common.RetryableFailure
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GeminiClient(private val http: HttpClient) : LlmClient {
    override val model = Config.str("GEMINI_MODEL", "gemini-2.5-flash")
    // Lazy so that construction (and pure parseResponse tests) never require the key.
    private val apiKey by lazy { Config.str("GEMINI_API_KEY") }
    private val inputUsdPerMTok = Config.double("LLM_INPUT_USD_PER_MTOK", 0.30)
    private val outputUsdPerMTok = Config.double("LLM_OUTPUT_USD_PER_MTOK", 2.50)

    override fun digest(source: String, title: String, url: String, text: String?): DigestResult {
        val prompt = buildPrompt(source, title, url, text)
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) }
                    },
                )
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            }
        }
        val request = HttpRequest.newBuilder(
            URI.create("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"),
        )
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: java.io.IOException) {
            throw RetryableFailure("Gemini request failed: ${e.message}", e)
        }
        when {
            response.statusCode() == 429 || response.statusCode() >= 500 ->
                throw RetryableFailure("Gemini returned ${response.statusCode()}")

            response.statusCode() != 200 ->
                error("Gemini returned ${response.statusCode()}: ${response.body().take(300)}")
        }
        return parseResponse(response.body(), model)
    }

    private fun buildPrompt(source: String, title: String, url: String, text: String?) = """
        You are the digest step of an AI-news pipeline. Summarize the item below.
        Respond with ONLY a JSON object, no markdown fences, with exactly these fields:
        {
          "summary_zh": "2-3 sentence summary in Traditional Chinese",
          "summary_en": "2-3 sentence summary in English",
          "tags": ["3-6 short lowercase tags"],
          "significance_score": 1-5 integer (5 = major industry news, 1 = niche),
          "category": one of "research" | "product" | "engineering" | "policy" | "other"
        }

        Source: $source
        Title: $title
        URL: $url
        ${text?.let { "Content:\n${it.take(8000)}" } ?: "Content: (not available, judge from the title)"}
    """.trimIndent()

    internal fun parseResponse(body: String, model: String): DigestResult {
        val root = Json.parseToJsonElement(body).jsonObject
        val payload = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini response missing candidates[0].content.parts[0].text: ${body.take(300)}")
        val digest = Json.parseToJsonElement(payload.trim()).jsonObject
        val usage = root["usageMetadata"]?.jsonObject
        val inputTokens = usage?.get("promptTokenCount")?.jsonPrimitive?.int ?: 0
        val outputTokens = usage?.get("candidatesTokenCount")?.jsonPrimitive?.int ?: 0
        return DigestResult(
            summaryZh = digest.required("summary_zh"),
            summaryEn = digest.required("summary_en"),
            tagsJson = digest["tags"]?.jsonArray?.toString() ?: "[]",
            significanceScore = digest["significance_score"]?.jsonPrimitive?.int?.coerceIn(1, 5)
                ?: error("digest JSON missing significance_score"),
            category = digest.required("category"),
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
        )
    }

    override fun cost(inputTokens: Int, outputTokens: Int): Double =
        inputTokens * inputUsdPerMTok / 1_000_000 + outputTokens * outputUsdPerMTok / 1_000_000

    private fun JsonObject.required(field: String): String =
        this[field]?.jsonPrimitive?.content ?: error("digest JSON missing $field")
}
