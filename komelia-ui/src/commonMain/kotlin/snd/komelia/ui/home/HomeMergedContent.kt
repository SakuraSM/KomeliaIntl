package snd.komelia.ui.home

import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.search.anyOfBooks
import snd.komga.client.search.anyOfSeries
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

internal fun localBookSearch(
    search: KomgaBookSearch,
    libraryIds: Collection<KomgaLibraryId>,
): KomgaBookSearch? {
    if (libraryIds.isEmpty()) return null
    val libraryCondition = anyOfBooks {
        libraryIds.forEach { libraryId -> library { isEqualTo(libraryId) } }
    }.toBookCondition()
    return search.copy(
        condition = search.condition
            ?.let { KomgaSearchCondition.AllOfBook(it, libraryCondition) }
            ?: libraryCondition,
    )
}

internal fun localSeriesSearch(
    search: KomgaSeriesSearch,
    libraryIds: Collection<KomgaLibraryId>,
): KomgaSeriesSearch? {
    if (libraryIds.isEmpty()) return null
    val libraryCondition = anyOfSeries {
        libraryIds.forEach { libraryId -> library { isEqualTo(libraryId) } }
    }.toSeriesCondition()
    return search.copy(
        condition = search.condition
            ?.let { KomgaSearchCondition.AllOfSeries(it, libraryCondition) }
            ?: libraryCondition,
    )
}

internal fun <T, K> mergeHomeItems(
    remote: List<T>,
    local: List<T>,
    limit: Int,
    idOf: (T) -> K,
    comparator: Comparator<T>? = null,
): List<T> {
    if (limit <= 0) return emptyList()
    val distinct = (remote + local).distinctBy(idOf)
    return (comparator?.let(distinct::sortedWith) ?: distinct).take(limit)
}

internal fun bookHomeComparator(sort: KomgaSort?): Comparator<KomeliaBook>? {
    val orders = (sort as? KomgaSort.KomgaBooksSort)?.orders.orEmpty()
    if (orders.isEmpty()) return null
    return Comparator { left, right ->
        orders.firstNotNullOfOrNull { order ->
            compareBookProperty(left, right, order.property)
                .takeIf { it != 0 }
                ?.let { if (order.direction == KomgaSort.Direction.DESC) -it else it }
        } ?: left.id.value.compareTo(right.id.value)
    }
}

internal fun seriesHomeComparator(sort: KomgaSort?): Comparator<KomgaSeries>? {
    val orders = (sort as? KomgaSort.KomgaSeriesSort)?.orders.orEmpty()
    if (orders.isEmpty()) return null
    return Comparator { left, right ->
        orders.firstNotNullOfOrNull { order ->
            compareSeriesProperty(left, right, order.property)
                .takeIf { it != 0 }
                ?.let { if (order.direction == KomgaSort.Direction.DESC) -it else it }
        } ?: left.id.value.compareTo(right.id.value)
    }
}

private fun compareBookProperty(left: KomeliaBook, right: KomeliaBook, property: String): Int = when (property) {
    "createdDate" -> left.created.compareTo(right.created)
    "name" -> left.name.compareTo(right.name, ignoreCase = true)
    "lastModified" -> left.lastModified.compareTo(right.lastModified)
    "metadata.numberSort" -> left.metadata.numberSort.compareTo(right.metadata.numberSort)
    "readProgress.readDate" -> compareValues(left.readProgress?.readDate, right.readProgress?.readDate)
    "metadata.releaseDate" -> compareValues(left.metadata.releaseDate, right.metadata.releaseDate)
    "series" -> left.seriesTitle.compareTo(right.seriesTitle, ignoreCase = true)
    "metadata.title" -> left.metadata.title.compareTo(right.metadata.title, ignoreCase = true)
    "metadata.pagesCount" -> left.media.pagesCount.compareTo(right.media.pagesCount)
    else -> 0
}

private fun compareSeriesProperty(left: KomgaSeries, right: KomgaSeries, property: String): Int = when (property) {
    "metadata.titleSort" -> left.metadata.titleSort.compareTo(right.metadata.titleSort, ignoreCase = true)
    "created" -> left.created.compareTo(right.created)
    "booksMetadata.releaseDate" -> compareValues(left.booksMetadata.releaseDate, right.booksMetadata.releaseDate)
    "name" -> left.name.compareTo(right.name, ignoreCase = true)
    "booksCount" -> left.booksCount.compareTo(right.booksCount)
    "lastModified" -> left.lastModified.compareTo(right.lastModified)
    else -> 0
}
