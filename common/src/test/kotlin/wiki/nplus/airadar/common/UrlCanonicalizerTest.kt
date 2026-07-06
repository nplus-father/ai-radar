package wiki.nplus.airadar.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UrlCanonicalizerTest {

    @Test
    fun `strips scheme www fragment trailing slash and tracking params`() {
        val a = UrlCanonicalizer.canonicalize("https://www.example.com/post/1/?utm_source=hn&utm_medium=social#top")
        val b = UrlCanonicalizer.canonicalize("http://example.com/post/1")
        assertEquals(b, a)
    }

    @Test
    fun `keeps meaningful query params in stable order`() {
        val a = UrlCanonicalizer.canonicalize("https://example.com/watch?v=abc&t=10")
        val b = UrlCanonicalizer.canonicalize("https://example.com/watch?t=10&v=abc")
        assertEquals(b, a)
        assertEquals("example.com/watch?t=10&v=abc", a)
    }

    @Test
    fun `same article from two sources hashes identically`() {
        val fromHn = UrlCanonicalizer.contentHash("Fable 5 released", "https://www.example.com/blog/fable-5?utm_source=hn")
        val fromReddit = UrlCanonicalizer.contentHash("Fable 5  Released ", "http://example.com/blog/fable-5/")
        assertEquals(fromReddit, fromHn)
    }

    @Test
    fun `different articles hash differently`() {
        val a = UrlCanonicalizer.contentHash("Post A", "https://example.com/a")
        val b = UrlCanonicalizer.contentHash("Post B", "https://example.com/b")
        assertNotEquals(b, a)
    }
}
