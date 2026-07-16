package wiki.nplus.airadar.publisher

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wiki.nplus.airadar.common.ItemRepository

/**
 * Renders one daily essay (news-echo) into markdown with frontmatter the site
 * can index. The essay body comes from the LLM verbatim; provenance (news
 * link, books used) is framed by the renderer so it cannot be hallucinated
 * away.
 */
object EssayRenderer {

    fun render(essay: ItemRepository.EssayRow, item: ItemRepository.ItemRow): String {
        val books = Json.parseToJsonElement(essay.booksJson).jsonArray.map { it.jsonObject }
        val bookList = books.joinToString("\n") { b ->
            val title = b["book_title"]?.jsonPrimitive?.content ?: "?"
            val chapter = b["chapter_title"]?.jsonPrimitive?.content
            "- 《$title》" + (chapter?.let { "｜$it" } ?: "")
        }
        return buildString {
            appendLine("---")
            appendLine("title: \"${essay.title.replace("\"", "\\\"")}\"")
            appendLine("date: ${essay.day}")
            appendLine("kind: essay")
            appendLine("---")
            appendLine()
            appendLine("> 回應新聞：[${item.title.replace("]", "）").replace("[", "（")}](${item.url})（${item.source}）")
            appendLine()
            appendLine(essay.essayMd.trim())
            appendLine()
            if (books.isNotEmpty()) {
                appendLine("---")
                appendLine()
                appendLine("本文書目：")
                appendLine()
                appendLine(bookList)
            }
        }
    }
}
