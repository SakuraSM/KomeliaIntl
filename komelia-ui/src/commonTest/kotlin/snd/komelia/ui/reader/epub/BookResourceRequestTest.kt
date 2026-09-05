package snd.komelia.ui.reader.epub

import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookResourceRequestTest {
    @Test
    fun `local book resources use the reader HTTP origin`() {
        val url = bookResourceUrl(KomgaBookId("local-book-1"), "OPS/text/chapter.xhtml")

        assertEquals(
            "http://komelia/api/v1/books/local-book-1/resource/OPS/text/chapter.xhtml",
            url,
        )
        assertTrue(isBookResourceRequest(url))
    }

    @Test
    fun `resource name normalization handles encoded paths queries and fragments`() {
        assertEquals(
            "OPS/text/Foo & Bar.xhtml",
            epubResourceName(
                "http://komelia/api/v1/books/local-book-1/resource/OPS/text/Foo%20%26%20Bar.xhtml?cache=1#chapter",
            ),
        )
        assertEquals("OPS/text/chapter.xhtml", epubResourceName("OPS/text/chapter.xhtml#chapter"))
    }
}
