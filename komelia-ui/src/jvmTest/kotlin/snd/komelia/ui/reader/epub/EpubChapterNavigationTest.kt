package snd.komelia.ui.reader.epub

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Instant

class EpubChapterNavigationTest {
    @Test fun chapterLaunchRetainsTheAnchorWhileNormalizingTheResourcePath() {
        val time = Instant.fromEpochMilliseconds(1000)
        val request = chapterStartProgression(
            "https://reader.invalid/api/v1/books/book/resource/EPUB/chapter%202.xhtml#section-two", null, time)
        assertEquals("EPUB/chapter 2.xhtml", request.locator.href)
        assertEquals(listOf("section-two"), request.locator.locations?.fragment)
        assertEquals(0f, request.locator.locations?.progression)
        assertEquals(time, request.modified)
    }

    @Test fun selectingAChapterDoesNotMutateTheSavedLocator() {
        val time = Instant.fromEpochMilliseconds(1000)
        val saved = chapterStartProgression("original.xhtml", null, time)
        val selected = chapterStartProgression("next.xhtml", saved, Instant.fromEpochMilliseconds(2000))
        assertEquals("original.xhtml", saved.locator.href)
        assertEquals(time, saved.modified)
        assertEquals("next.xhtml", selected.locator.href)
    }

    @Test fun anchorPrefixUsesElementIdentityAndLeavesThePublicationIntact() {
        val chapter = Ksoup.parse("<p>Same</p><div>Before <span>inside</span><h2 id='target'>Same</h2><p>After</p></div>").body()
        val original = chapter.html()
        val prefix = epubPrefixBeforeElement(chapter, assertNotNull(chapter.getElementById("target")))
        assertEquals("SameBeforeinside", prefix.text().replace(Regex("\\s+"), ""))
        assertEquals(original, chapter.html())
    }
}
