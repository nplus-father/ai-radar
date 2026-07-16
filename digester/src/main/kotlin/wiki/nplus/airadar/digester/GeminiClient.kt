package wiki.nplus.airadar.digester

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import wiki.nplus.airadar.common.Config
import wiki.nplus.airadar.common.DigestResult
import wiki.nplus.airadar.common.ItemRepository
import wiki.nplus.airadar.common.RetryableFailure
import wiki.nplus.airadar.common.SelectResult
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * One instance per model tier: the defaults are the cheap digest model, the
 * curator constructs a second instance with the SELECT_* config (ADR-009).
 */
class GeminiClient(
    private val http: HttpClient,
    override val model: String = Config.str("GEMINI_MODEL", "gemini-2.5-flash"),
    private val inputUsdPerMTok: Double = Config.double("LLM_INPUT_USD_PER_MTOK", 0.30),
    private val outputUsdPerMTok: Double = Config.double("LLM_OUTPUT_USD_PER_MTOK", 2.50),
) : LlmClient {
    // Lazy so that construction (and pure parse tests) never require the key.
    private val apiKey by lazy { Config.str("GEMINI_API_KEY") }

    override fun digest(source: String, title: String, url: String, text: String?): DigestResult =
        parseResponse(generate(buildDigestPrompt(source, title, url, text)), model)

    override fun select(candidates: List<ItemRepository.DigestedItem>, maxPicks: Int): SelectResult =
        parseSelection(generate(buildSelectPrompt(candidates, maxPicks)), model)

    private fun generate(prompt: String): String {
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
        return response.body()
    }

    private fun buildDigestPrompt(source: String, title: String, url: String, text: String?) = """
        You are the senior editor of a daily AI-and-technology intelligence brief read by
        busy engineers and founders. You are given ONE candidate item. Write a tight,
        high-signal digest that says what happened AND why it matters — no filler.

        Respond with ONLY a JSON object (no markdown fences) with exactly these fields:
        {
          "summary_zh": "2-4 sentence digest in Traditional Chinese: the substance AND why it matters. Concrete, factual, self-contained.",
          "summary_en": "2-4 sentence digest in English: the substance AND why it matters. Concrete, factual, self-contained.",
          "tags": ["3-6 short lowercase topical tags"],
          "significance_score": integer 1-5 (5 = major, industry-shifting; 4 = notable across the field; 3 = useful to a sub-field; 2 = niche; 1 = trivial). Be a strict grader — reserve 5 for genuinely major news.,
          "category": one of "research" | "product" | "engineering" | "policy" | "other"
        }

        Guidance:
        - Lead with the substance; do not open with "This article discusses...".
        - For a research paper, state the concrete result/claim and its practical implication.
        - Do not invent details. If content is missing, judge conservatively from the title and lower the score.

        Source: $source
        Title: $title
        URL: $url
        ${text?.let { "Content:\n${it.take(12000)}" } ?: "Content: (not available — judge from the title only, and score conservatively)"}
    """.trimIndent()

    private fun buildSelectPrompt(candidates: List<ItemRepository.DigestedItem>, maxPicks: Int): String {
        val list = buildJsonArray {
            candidates.forEach { c ->
                add(
                    buildJsonObject {
                        put("id", c.itemId)
                        put("title", c.title)
                        put("score", c.significanceScore)
                        put("category", c.category)
                        put("summary", c.summaryEn)
                    },
                )
            }
        }
        return """
            You are the editor-in-chief of a daily commentary column. Each day ONE news item
            is paired with insights from one or two books (organizational theory, history of
            technology, economics, engineering practice, ...) and turned into a deep essay.

            Below are today's ${candidates.size} digested candidates. Choose AT MOST $maxPicks that are
            genuinely worth that treatment. This is a RELATIVE ranking across the whole set:

            - Favor items with lasting significance — a shift in how people build, work, or
              govern — over incremental releases and benchmark bumps.
            - Favor items where a book could plausibly add a frame the news itself lacks.
            - Be strict: picking fewer, or none, is a perfectly good answer.

            Respond with ONLY a JSON object (no markdown fences):
            {
              "picks": [
                {"id": <candidate id>, "reason": "1-2 sentences in Traditional Chinese: why this deserves a book-informed deep commentary."}
              ]
            }

            Candidates:
            $list
        """.trimIndent()
    }

    internal fun parseResponse(body: String, model: String): DigestResult {
        val (payload, inputTokens, outputTokens) = extractPayload(body)
        val digest = Json.parseToJsonElement(payload).jsonObject
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

    internal fun parseSelection(body: String, model: String): SelectResult {
        val (payload, inputTokens, outputTokens) = extractPayload(body)
        val picks = Json.parseToJsonElement(payload).jsonObject["picks"]?.jsonArray
            ?: error("selection JSON missing picks")
        return SelectResult(
            picks = picks.map { p ->
                val pick = p.jsonObject
                SelectResult.Pick(
                    itemId = pick["id"]?.jsonPrimitive?.long ?: error("selection pick missing id"),
                    reason = pick.required("reason"),
                )
            },
            model = model,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
        )
    }

    private data class Payload(val text: String, val inputTokens: Int, val outputTokens: Int)

    private fun extractPayload(body: String): Payload {
        val root = Json.parseToJsonElement(body).jsonObject
        val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: error("Gemini response missing candidates[0].content.parts[0].text: ${body.take(300)}")
        val usage = root["usageMetadata"]?.jsonObject
        return Payload(
            text = text.trim(),
            inputTokens = usage?.get("promptTokenCount")?.jsonPrimitive?.int ?: 0,
            outputTokens = usage?.get("candidatesTokenCount")?.jsonPrimitive?.int ?: 0,
        )
    }

    override fun cost(inputTokens: Int, outputTokens: Int): Double =
        inputTokens * inputUsdPerMTok / 1_000_000 + outputTokens * outputUsdPerMTok / 1_000_000

    private fun JsonObject.required(field: String): String =
        this[field]?.jsonPrimitive?.content ?: error("JSON missing $field")
}
