package snd.webview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertContentEquals

class MacResourceUrlMapperTest {
    private val mapper = MacResourceUrlMapper("testsession", 43123)

    @Test fun mapsBookResourcesAndRejectsOtherSessions() {
        val original = "https://books.example.invalid/api/v1/books/123/resource/EPUB/cover%20art.png?size=2#part"
        val mapped = mapper.toWebUrl(original)
        assertEquals("http://127.0.0.1:43123/testsession/api/v1/books/123/resource/EPUB/cover%20art.png?size=2#part", mapped)
        assertEquals("http://komelia/api/v1/books/123/resource/EPUB/cover%20art.png?size=2", mapper.toOriginalUrl(mapped))
        assertNull(mapper.toOriginalUrl("http://127.0.0.1:43123/another/api/v1/books/123/resource/a.png"))
        assertEquals("https://other.example.invalid/external", mapper.toWebUrl("https://other.example.invalid/external"))
    }

    @Test fun changesResourceAttributesWithoutChangingVisibleBookText() {
        val markup = """<p>http://komelia/api/v1/books/1/resource/a.png</p><img src="http://komelia/api/v1/books/1/resource/a.png"><div style="background:url('https://books.example.invalid/api/v1/books/1/resource/a.png')"></div>"""
        val rewritten = mapper.rewriteMarkup(markup)
        assertEquals("""<p>http://komelia/api/v1/books/1/resource/a.png</p><img src="http://127.0.0.1:43123/testsession/api/v1/books/1/resource/a.png"><div style="background:url('http://127.0.0.1:43123/testsession/api/v1/books/1/resource/a.png')"></div>""", rewritten)
    }

    @Test fun preservesTextCommentsAndScriptsWhenRewritingMarkup() {
        val url = "http://komelia/api/v1/books/1/resource/a.png"
        val markup = """<p>src="$url" and url($url)</p><!-- <img src="$url"> --><script>const example = 'src="$url"';</script><style>.cover { background: url('$url') }</style><img src="$url">"""
        val expected = """<p>src="$url" and url($url)</p><!-- <img src="$url"> --><script>const example = 'src="$url"';</script><style>.cover { background: url('http://127.0.0.1:43123/testsession/api/v1/books/1/resource/a.png') }</style><img src="http://127.0.0.1:43123/testsession/api/v1/books/1/resource/a.png">"""
        assertEquals(expected, mapper.rewriteMarkup(markup))
        val attributes = """<img title='src="$url"' src=$url>"""
        assertEquals("""<img title='src="$url"' src=http://127.0.0.1:43123/testsession/api/v1/books/1/resource/a.png>""", mapper.rewriteMarkup(attributes))
    }

    @Test fun rewritesStylesheetsWithoutChangingCssStringsOrComments() {
        val url = "http://komelia/api/v1/books/1/resource/a.png"
        val response = """{"result":{"styleSheet":"/* url($url) */ p::before{content:'url($url)'} img{background:url('$url')}"}}"""
        val style = Json.parseToJsonElement(mapper.rewriteResult("getBookData", response)).jsonObject["result"]!!
            .jsonObject["styleSheet"]!!.jsonPrimitive.content
        assertEquals("""/* url($url) */ p::before{content:'url($url)'} img{background:url('http://127.0.0.1:43123/testsession/api/v1/books/1/resource/a.png')}""", style)
    }

    @Test fun rewritesTextResourcesButLeavesBinaryAndInvalidTextAlone() {
        val css = """p::before{content:'url(http://komelia/api/v1/books/1/resource/a)'} img{background:url(http://komelia/api/v1/books/1/resource/a)}"""
        val expected = css.replace("background:url(http://komelia", "background:url(http://127.0.0.1:43123/testsession")
        assertEquals(expected, mapper.rewriteResource("text/css", css.encodeToByteArray()).decodeToString())
        val binary = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x80.toByte())
        assertContentEquals(binary, mapper.rewriteResource("image/png", binary))
        assertContentEquals(binary, mapper.rewriteResource("text/css", binary))
        val response = Json.encodeToString(mapOf("result" to css))
        val result = mapper.rewriteResult("d2ReaderGetContent", response, """["http://komelia/api/v1/books/1/resource/style.css"]""")
        assertEquals(expected, Json.parseToJsonElement(result).jsonObject["result"]!!.jsonPrimitive.content)
    }

    @Test fun mapsReaderResourcesWithoutChangingChapterIdentityOrTitle() {
        val response = """{"result":{"title":"https://books.example.invalid/api/v1/books/1/resource/title","elementHtml":"<div id='https://books.example.invalid/api/v1/books/1/resource/chapter'><img src='https://books.example.invalid/api/v1/books/1/resource/image.png'></div>","styleSheet":"a{background:url(https://books.example.invalid/api/v1/books/1/resource/a.png)}","imageUrls":["https://books.example.invalid/api/v1/books/1/resource/image.png"],"sections":[{"reference":"https://books.example.invalid/api/v1/books/1/resource/chapter"}]}}"""
        val result = Json.parseToJsonElement(mapper.rewriteResult("getBookData", response)).jsonObject["result"]!!.jsonObject
        assertEquals("https://books.example.invalid/api/v1/books/1/resource/title", result["title"]!!.jsonPrimitive.content)
        assertEquals("https://books.example.invalid/api/v1/books/1/resource/chapter", result["sections"]!!.jsonArray[0].jsonObject["reference"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:43123/testsession/api/v1/books/1/resource/image.png", result["imageUrls"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals(true, result["elementHtml"]!!.jsonPrimitive.content.contains("id='https://books.example.invalid/"))
        assertEquals(true, result["elementHtml"]!!.jsonPrimitive.content.contains("src='http://127.0.0.1:43123/testsession/"))
    }

    @Test fun mapsPublicationLinksAndServerBase() {
        val links = """{"result":{"title":"Keep this title","readingOrder":[{"href":"https://books.example.invalid/api/v1/books/1/resource/chapter.xhtml"}]}}"""
        val result = Json.parseToJsonElement(mapper.rewriteResult("getPublication", links)).jsonObject["result"]!!.jsonObject
        assertEquals("Keep this title", result["title"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:43123/testsession/api/v1/books/1/resource/chapter.xhtml", result["readingOrder"]!!.jsonArray[0].jsonObject["href"]!!.jsonPrimitive.content)
        assertEquals("""{"result":"http://127.0.0.1:43123/testsession/base"}""", mapper.rewriteResult("getServerUrl", """{"result":"https://books.example.invalid/base"}"""))
    }
}
