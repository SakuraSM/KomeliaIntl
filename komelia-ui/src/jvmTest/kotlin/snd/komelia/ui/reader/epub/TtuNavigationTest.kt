package snd.komelia.ui.reader.epub

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import snd.komga.client.book.WPLink
import kotlin.test.Test
import kotlin.test.assertEquals

class TtuNavigationTest {
    @Test fun preservesNestedTitlesAndFragmentsWhileMatchingReadingOrderResources() {
        val readingOrder = listOf(
            WPLink(href = "http://komelia/api/v1/books/synthetic/resource/EPUB/first.xhtml"),
            WPLink(href = "http://komelia/api/v1/books/synthetic/resource/EPUB/chapter%20two.xhtml"),
        )
        val toc = listOf(
            WPLink(title = "Part one", href = "EPUB/first.xhtml", children = listOf(
                WPLink(title = "Nested target", href = "EPUB/chapter%20two.xhtml#section%201"),
            )),
            WPLink(title = "Group", children = listOf(WPLink(title = "Missing", href = "missing.xhtml"))),
        )

        assertEquals(listOf(
            TtuTocEntry("Part one", readingOrder[0].href, listOf(
                TtuTocEntry("Nested target", "${readingOrder[1].href}#section%201"),
            )),
            TtuTocEntry("Group", null, listOf(TtuTocEntry("Missing", null))),
        ), ttuNavigationEntries(toc, readingOrder))
    }

    @Test fun readerPayloadIncludesTheNestedNavigationTree() {
        val book = TtsuReaderState.TtuBookData(
            id = "synthetic", title = "Book", styleSheet = "", elementHtml = "",
            imageUrls = emptyList(), coverImage = null, hasThumb = false, characters = 0,
            sections = emptyList(), toc = listOf(TtuTocEntry("Part", "first.xhtml", listOf(
                TtuTocEntry("Target", "second.xhtml#anchor"),
            ))), lastBookModified = 0, lastBookOpen = 0,
        )
        val root = Json.encodeToJsonElement(TtsuReaderState.TtuBookData.serializer(), book)
            .jsonObject.getValue("toc").jsonArray.single().jsonObject
        val child = root.getValue("children").jsonArray.single().jsonObject
        assertEquals("Part", root.getValue("title").jsonPrimitive.content)
        assertEquals("second.xhtml#anchor", child.getValue("reference").jsonPrimitive.content)
    }
}
