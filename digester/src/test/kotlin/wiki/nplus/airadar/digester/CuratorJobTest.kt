package wiki.nplus.airadar.digester

import wiki.nplus.airadar.common.ItemRepository
import wiki.nplus.airadar.common.SelectResult
import kotlin.test.Test
import kotlin.test.assertEquals

class CuratorJobTest {

    private fun candidate(id: Long) = ItemRepository.SelectionCandidate(
        item = ItemRepository.DigestedItem(
            itemId = id,
            source = "hn",
            url = "https://example.com/$id",
            title = "item $id",
            summaryZh = "摘要",
            summaryEn = "summary",
            tagsJson = "[]",
            significanceScore = 4,
            category = "product",
        ),
        topBookDistance = 1.05,
        booksJson = """[{"book_id":"b1","title_zh":"書一","distance":1.05}]""",
    )

    private fun result(vararg ids: Long) = SelectResult(
        picks = ids.map { SelectResult.Pick(it, "reason $it") },
        model = "test",
        inputTokens = 0,
        outputTokens = 0,
    )

    @Test
    fun `hallucinated ids are dropped`() {
        val picks = CuratorJob.validatePicks(result(1, 99), listOf(candidate(1), candidate(2)), 3)
        assertEquals(listOf(1L), picks.map { it.itemId })
    }

    @Test
    fun `picks beyond the cap are clamped`() {
        val candidates = (1L..5L).map { candidate(it) }
        val picks = CuratorJob.validatePicks(result(1, 2, 3, 4), candidates, 2)
        assertEquals(listOf(1L, 2L), picks.map { it.itemId })
    }

    @Test
    fun `duplicate ids collapse to one pick`() {
        val picks = CuratorJob.validatePicks(result(1, 1, 2), listOf(candidate(1), candidate(2)), 3)
        assertEquals(listOf(1L, 2L), picks.map { it.itemId })
    }

    @Test
    fun `empty selection stays empty`() {
        assertEquals(0, CuratorJob.validatePicks(result(), listOf(candidate(1)), 3).size)
    }
}
