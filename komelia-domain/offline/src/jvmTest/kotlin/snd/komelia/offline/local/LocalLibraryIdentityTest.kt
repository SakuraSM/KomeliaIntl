package snd.komelia.offline.local

import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalLibraryIdentityTest {
    @Test
    fun identifiesOnlyLocalLibraryIds() {
        assertTrue(KomgaLibraryId("local-library-device-books").isLocalLibrary())
        assertFalse(KomgaLibraryId("remote-library").isLocalLibrary())
        assertFalse(KomgaLibraryId("my-local-library-copy").isLocalLibrary())
    }

    @Test
    fun identifiesOnlyLocalSeriesIds() {
        assertTrue(KomgaSeriesId("local-series-device-books").isLocalSeries())
        assertFalse(KomgaSeriesId("remote-series").isLocalSeries())
    }

    @Test
    fun identifiesOnlyLocalBookIds() {
        assertTrue(KomgaBookId("local-book-device-book").isLocalBook())
        assertFalse(KomgaBookId("remote-book").isLocalBook())
    }
}
