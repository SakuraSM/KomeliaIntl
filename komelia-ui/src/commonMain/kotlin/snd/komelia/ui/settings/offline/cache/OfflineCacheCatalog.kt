package snd.komelia.ui.settings.offline.cache

import snd.komelia.offline.local.isLocalLibrary
import snd.komga.client.library.KomgaLibraryId

internal enum class OfflineCacheMediaKind {
    COMIC,
    EPUB,
    PDF,
    OTHER,
}

internal data class OfflineCacheSeriesRecord(
    val id: String,
    val title: String,
    val libraryId: String = "",
)

internal data class OfflineCacheBookRecord(
    val id: String,
    val seriesId: String,
    val title: String,
    val mediaKind: OfflineCacheMediaKind,
    val sizeBytes: Long,
    val updatedEpochSeconds: Long,
    val isAvailable: Boolean,
    val libraryId: String = "",
)

internal data class OfflineCacheSeriesItem(
    val id: String,
    val title: String,
    val books: List<OfflineCacheBookRecord>,
) {
    val sizeBytes: Long = books.sumOf { it.sizeBytes }
    val missingBookCount: Int = books.count { !it.isAvailable }
}

internal data class OfflineCacheCatalog(
    val series: List<OfflineCacheSeriesItem>,
    val orphanBooks: List<OfflineCacheBookRecord>,
) {
    val books: List<OfflineCacheBookRecord> = series.flatMap { it.books } + orphanBooks
    val bookCount: Int = books.size
    val totalSizeBytes: Long = books.sumOf { it.sizeBytes }
    val missingBookCount: Int = books.count { !it.isAvailable }

    fun filtered(mediaKind: OfflineCacheMediaKind?): OfflineCacheCatalog {
        if (mediaKind == null) return this
        return OfflineCacheCatalog(
            series = series.mapNotNull { item ->
                item.copy(books = item.books.filter { it.mediaKind == mediaKind })
                    .takeIf { it.books.isNotEmpty() }
            },
            orphanBooks = orphanBooks.filter { it.mediaKind == mediaKind },
        )
    }
}

internal fun buildOfflineCacheCatalog(
    series: List<OfflineCacheSeriesRecord>,
    books: List<OfflineCacheBookRecord>,
): OfflineCacheCatalog {
    val remoteSeries = series.filterNot { KomgaLibraryId(it.libraryId).isLocalLibrary() }
    val remoteBooks = books.filterNot { KomgaLibraryId(it.libraryId).isLocalLibrary() }
    val seriesById = remoteSeries.associateBy { it.id }
    val groupedBooks = remoteBooks.groupBy { it.seriesId }
    val seriesItems = remoteSeries.mapNotNull { record ->
        val seriesBooks = groupedBooks[record.id].orEmpty()
        if (seriesBooks.isEmpty()) null
        else OfflineCacheSeriesItem(record.id, record.title, seriesBooks.sortedBy { it.title })
    }
    val orphanBooks = remoteBooks.filter { it.seriesId !in seriesById }.sortedBy { it.title }
    return OfflineCacheCatalog(seriesItems, orphanBooks)
}
