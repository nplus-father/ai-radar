package wiki.nplus.airadar.publisher

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wiki.nplus.airadar.common.ItemRepository

/**
 * Renders one daily essay (news-echo) into markdown. Provenance — the news the
 * essay answers and the books it draws on — is emitted as structured
 * frontmatter so the site can render it as a first-class header (the triplet:
 * news × book → essay) instead of parsing prose. The body is the LLM's verbatim
 * essay markdown, nothing else.
 *
 * `book_id` from retrieval is the library slug, which is also the book's
 * published Hugo site path (nplus.wiki/<slug>/); the site builds the cover and
 * link URLs from it. chapter_id is "<slug>:<path>", a fallback when book_id is
 * blank so a title-only book still resolves a slug.
 */
object EssayRenderer {

    fun render(
        essay: ItemRepository.EssayRow,
        item: ItemRepository.ItemRow,
        newsSummary: String? = null,
    ): String {
        val books = Json.parseToJsonElement(essay.booksJson).jsonArray.map { it.jsonObject }
        return buildString {
            appendLine("---")
            appendLine("title: ${yaml(essay.title)}")
            appendLine("date: ${essay.day}")
            appendLine("kind: essay")
            // The model that actually wrote this piece, recorded per essay
            // rather than as a site-wide footnote: the essay tier is a config
            // value and older essays were written by whatever it was then. A
            // reader is owed the specific name, not "AI".
            appendLine("model: ${yaml(essay.model)}")
            appendLine("news:")
            appendLine("  title: ${yaml(item.title)}")
            appendLine("  url: ${yaml(item.url)}")
            appendLine("  source: ${yaml(item.source)}")
            if (!newsSummary.isNullOrBlank()) appendLine("  summary: ${yaml(newsSummary)}")
            if (books.isNotEmpty()) {
                appendLine("books:")
                books.forEach { b ->
                    val title = b["book_title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "?"
                    val chapter = b["chapter_title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val chapterId = b["chapter_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    val slug = bookSlug(b)
                    appendLine("  - title: ${yaml(title)}")
                    if (chapter != null) appendLine("    chapter: ${yaml(chapter)}")
                    if (slug != null) appendLine("    slug: ${yaml(slug)}")
                    // "<slug>:<content-path>" — the site deep-links to the chapter's deployed page.
                    if (chapterId != null) appendLine("    chapter_id: ${yaml(chapterId)}")
                }
            }
            appendLine("---")
            appendLine()
            appendLine(essay.essayMd.trim())
        }
    }

    /** book_id (== library slug) if present, else the slug prefix of chapter_id. */
    private fun bookSlug(b: JsonObject): String? {
        b["book_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { return it }
        b["chapter_id"]?.jsonPrimitive?.content
            ?.substringBefore(':', "")?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    /** A double-quoted YAML scalar; newlines flattened so one summary stays one line. */
    private fun yaml(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""
}
