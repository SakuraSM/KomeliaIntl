package snd.komelia.ui.reader.epub

import kotlin.test.Test
import kotlin.test.assertEquals

class TtuStylesheetUrlsTest {
    @Test fun resolvesRelativeResourcesWithoutChangingCssTextOrExternalUrls() {
        val css = """/* url(comment.png) */ p:before{content:'url(text.png)'} .cover{background:url('../images/cover.png?x=1#part')} @font-face{src:url(font.woff2)} .inline{background:url(data:image/png;base64,abc)} a{filter:url(#effect)} b{background:url(https://example.invalid/p.png)}"""
        val expected = """/* url(comment.png) */ p:before{content:'url(text.png)'} .cover{background:url('http://komelia/api/v1/books/1/resource/EPUB/images/cover.png?x=1#part')} @font-face{src:url(http://komelia/api/v1/books/1/resource/EPUB/styles/font.woff2)} .inline{background:url(data:image/png;base64,abc)} a{filter:url(#effect)} b{background:url(https://example.invalid/p.png)}"""
        assertEquals(expected, resolveStylesheetUrls(css, "http://komelia/api/v1/books/1/resource/EPUB/styles/book.css"))
    }
}
