package wiki.nplus.airadar.producers

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedParserTest {

    @Test
    fun `parses rss 2_0`() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <title>Blog</title>
              <item><title>Post A</title><link>https://x.test/a</link><guid>a-1</guid><pubDate>Mon, 06 Jul 2026 10:00:00 GMT</pubDate></item>
              <item><title>Post B</title><link>https://x.test/b</link></item>
            </channel></rss>
        """.trimIndent()
        val items = FeedParser.parse(xml)
        assertEquals(2, items.size)
        assertEquals("a-1", items[0].id)
        assertEquals("https://x.test/a", items[0].link)
        assertEquals("https://x.test/b", items[1].id)
    }

    @Test
    fun `parses atom with alternate link`() {
        val xml = """
            <?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>urn:1</id><title>Entry 1</title>
                <link rel="self" href="https://x.test/self"/>
                <link rel="alternate" href="https://x.test/entry-1"/>
                <published>2026-07-06T10:00:00Z</published>
              </entry>
            </feed>
        """.trimIndent()
        val items = FeedParser.parse(xml)
        assertEquals(1, items.size)
        assertEquals("https://x.test/entry-1", items[0].link)
        assertEquals("2026-07-06T10:00:00Z", items[0].published)
    }
}
