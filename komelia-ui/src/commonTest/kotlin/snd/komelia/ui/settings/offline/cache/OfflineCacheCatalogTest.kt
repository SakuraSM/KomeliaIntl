package snd.komelia.ui.settings.offline.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineCacheCatalogTest {
    @Test
    fun localSourceBooksNeverAppearInTheRemoteDownloadCache() {
        val catalog = buildOfflineCacheCatalog(
            series = listOf(
                OfflineCacheSeriesRecord("remote-series", "Remote", "remote-library"),
                OfflineCacheSeriesRecord("local-series", "Local", "local-library-device"),
            ),
            books = listOf(
                book("remote-book", "remote-series", OfflineCacheMediaKind.COMIC, 10),
                book(
                    "local-book",
                    "local-series",
                    OfflineCacheMediaKind.EPUB,
                    20,
                    libraryId = "local-library-device",
                ),
            ),
        )

        assertEquals(listOf("remote-book"), catalog.books.map { it.id })
        assertEquals(listOf("remote-series"), catalog.series.map { it.id })
    }

    @Test
    fun catalogGroupsBooksAndKeepsOrphansVisible() {
        val catalog = buildOfflineCacheCatalog(
            series = listOf(OfflineCacheSeriesRecord("series-1", "Series one")),
            books = listOf(
                book("book-1", "series-1", OfflineCacheMediaKind.COMIC, 10),
                book("book-2", "missing-series", OfflineCacheMediaKind.EPUB, 20),
            ),
        )

        assertEquals(2, catalog.bookCount)
        assertEquals(30, catalog.totalSizeBytes)
        assertEquals(listOf("book-1"), catalog.series.single().books.map { it.id })
        assertEquals(listOf("book-2"), catalog.orphanBooks.map { it.id })
    }

    @Test
    fun categoryFilterPreservesSeriesContextAndMissingFiles() {
        val catalog = buildOfflineCacheCatalog(
            series = listOf(OfflineCacheSeriesRecord("series-1", "Series one")),
            books = listOf(
                book("comic", "series-1", OfflineCacheMediaKind.COMIC, 10),
                book("epub", "series-1", OfflineCacheMediaKind.EPUB, 20, available = false),
            ),
        )

        val filtered = catalog.filtered(OfflineCacheMediaKind.EPUB)

        assertEquals(listOf("epub"), filtered.series.single().books.map { it.id })
        assertEquals(1, filtered.missingBookCount)
        assertFalse(filtered.series.single().books.single().isAvailable)
    }

    @Test
    fun emptySeriesAreNotShownAndAllFilterRestoresEveryBook() {
        val catalog = buildOfflineCacheCatalog(
            series = listOf(
                OfflineCacheSeriesRecord("empty", "Empty"),
                OfflineCacheSeriesRecord("series-1", "Series one"),
            ),
            books = listOf(book("pdf", "series-1", OfflineCacheMediaKind.PDF, 42)),
        )

        assertEquals(listOf("series-1"), catalog.series.map { it.id })
        assertTrue(catalog.filtered(null).series.single().books.single().isAvailable)
        assertEquals(42, catalog.filtered(null).totalSizeBytes)
    }

    private fun book(
        id: String,
        seriesId: String,
        kind: OfflineCacheMediaKind,
        size: Long,
        available: Boolean = true,
        libraryId: String = "remote-library",
    ) = OfflineCacheBookRecord(
        id = id,
        seriesId = seriesId,
        title = id,
        mediaKind = kind,
        sizeBytes = size,
        updatedEpochSeconds = 1,
        isAvailable = available,
        libraryId = libraryId,
    )
}
