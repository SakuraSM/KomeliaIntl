package snd.komelia.ui

import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentApiSelectionTest {
    @Test
    fun `local book selects offline content api`() {
        assertTrue(
            shouldUseOfflineContentApi(
                bookLibraryId = KomgaLibraryId("local-library-books"),
                seriesLibraryId = null,
            )
        )
    }

    @Test
    fun `local oneshot series selects offline content api before book is loaded`() {
        assertTrue(
            shouldUseOfflineContentApi(
                bookLibraryId = null,
                seriesLibraryId = KomgaLibraryId("local-library-oneshots"),
            )
        )
    }

    @Test
    fun `remote content keeps remote content api`() {
        assertFalse(
            shouldUseOfflineContentApi(
                bookLibraryId = KomgaLibraryId("remote-library"),
                seriesLibraryId = KomgaLibraryId("remote-library"),
            )
        )
    }

    @Test
    fun `local ids select offline content api before metadata is loaded`() {
        assertTrue(
            shouldUseOfflineContentApi(
                bookLibraryId = null,
                seriesLibraryId = null,
                bookId = KomgaBookId("local-book-123"),
            )
        )
        assertTrue(
            shouldUseOfflineContentApi(
                bookLibraryId = null,
                seriesLibraryId = null,
                seriesId = KomgaSeriesId("local-series-123"),
            )
        )
    }
}
