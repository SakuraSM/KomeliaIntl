package snd.komelia.ui.home

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks
import snd.komga.client.search.anyOfBooks

internal const val RECENTLY_READ_BOOKS_LIMIT = 20

internal fun recentlyReadBooksSearch(): KomgaBookSearch = KomgaBookSearch(
    anyOfBooks {
        readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) }
        readStatus { isEqualTo(KomgaReadStatus.READ) }
    }.toBookCondition(),
)

/** Uses the selected catalog identity and only that identity's local-folder libraries. */
internal suspend fun loadRecentlyReadBooks(
    bookApi: KomgaBookApi,
    offlineBookApi: KomgaBookApi?,
    localLibraryIds: List<KomgaLibraryId>,
): List<KomeliaBook> = coroutineScope {
    val search = recentlyReadBooksSearch()
    val page = KomgaPageRequest(size = RECENTLY_READ_BOOKS_LIMIT, sort = KomgaSort.KomgaBooksSort.byReadDateDesc())
    val primary = async { bookApi.getBookList(search, page).content }
    val local = async {
        val localSearch = localBookSearch(search, localLibraryIds)
        if (localSearch != null && offlineBookApi != null) offlineBookApi.getBookList(localSearch, page).content
        else emptyList()
    }
    mergeHomeItems(
        remote = primary.await().filter { !it.deleted && it.readProgress != null },
        local = local.await().filter { !it.deleted && it.readProgress != null },
        limit = RECENTLY_READ_BOOKS_LIMIT,
        idOf = { it.id },
        comparator = bookHomeComparator(page.sort),
    )
}

/** The overview replaces these exact read-date queries in All; custom groups stay selectable. */
internal fun isReadingOverviewFilter(filter: HomeScreenFilter): Boolean {
    if (filter !is BooksHomeScreenFilter.CustomFilter || !filter.textSearch.isNullOrBlank()) return false
    val sort = filter.pageRequest?.sort as? KomgaSort.KomgaBooksSort ?: return false
    if (sort.orders != KomgaSort.KomgaBooksSort.byReadDateDesc().orders) return false
    return listOf(KomgaReadStatus.IN_PROGRESS, KomgaReadStatus.READ).any { status ->
        filter.filter == allOfBooks { readStatus { isEqualTo(status) } }.toBookCondition()
    }
}
