package wiki.nplus.airadar.publisher

import wiki.nplus.airadar.common.ItemRepository.DigestedItem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigestRendererTest {

    private fun item(id: Long, score: Int, title: String = "Item $id") = DigestedItem(
        itemId = id,
        source = "hn",
        url = "https://example.com/$id",
        title = title,
        summaryZh = "摘要 $id",
        summaryEn = "summary $id",
        tagsJson = """["llm"]""",
        significanceScore = score,
        category = "research",
    )

    @Test
    fun `high scores go to highlights, low scores to also-seen`() {
        val page = DigestRenderer.renderDaily(LocalDate.of(2026, 7, 6), listOf(item(1, 5), item(2, 2)))
        assertTrue(page.contains("## Highlights"))
        assertTrue(page.contains("### [Item 1]"))
        assertTrue(page.contains("## Also seen"))
        assertTrue(page.contains("- [Item 2]"))
    }

    @Test
    fun `rendering is deterministic (idempotent regeneration)`() {
        val items = listOf(item(1, 4), item(2, 3))
        val day = LocalDate.of(2026, 7, 6)
        assertEquals(DigestRenderer.renderDaily(day, items), DigestRenderer.renderDaily(day, items))
    }

    @Test
    fun `empty day renders a valid page`() {
        val page = DigestRenderer.renderDaily(LocalDate.of(2026, 7, 6), emptyList())
        assertTrue(page.contains("itemCount: 0"))
        assertTrue(page.contains("No items digested today."))
    }
}
