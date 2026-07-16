package wiki.nplus.airadar.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LibraryClientTest {

    @Test
    fun `parses a search response and extracts the gate signal`() {
        val body = """
            {"query": "q",
             "books": [{"book_id": "b1", "title_zh": "書一", "distance": 1.0329}, {"book_id": "b2", "distance": 1.09}],
             "passages": [{"chapter_id": "b1:c1", "book_title": "書一", "distance": 1.05, "score": 0.0164}]}
        """.trimIndent()
        val result = HttpLibraryClient.parseSearch(body)
        assertEquals(1.0329, result.topBookDistance)
        assertEquals(true, result.booksJson.contains("b2"))
        assertEquals(true, result.passagesJson.contains("b1:c1"))
    }

    @Test
    fun `empty books means no gate signal`() {
        val result = HttpLibraryClient.parseSearch("""{"books": [], "passages": []}""")
        assertNull(result.topBookDistance)
    }

    @Test
    fun `missing fields fail fast`() {
        assertFailsWith<IllegalStateException> { HttpLibraryClient.parseSearch("""{"passages": []}""") }
    }
}
