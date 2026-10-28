package snd.komelia.ui.home

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import androidx.compose.ui.unit.dp
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.Page
import snd.komga.client.common.Pageable
import snd.komga.client.common.Sort
import snd.komga.client.library.KomgaLibraryId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class RecentlyReadBooksTest {
    @Test fun completedAndUnfinishedBooksShareRealReadDateOrdering() = runTest {
        val reading = homeReadingBook("reading", 1_000)
        val completed = homeReadingBook("finished", 3_000).let { it.copy(readProgress = it.readProgress!!.copy(completed = true)) }
        val unread = homeReadingBook("unread", 4_000).copy(readProgress = null)
        val deleted = homeReadingBook("deleted", 5_000).copy(deleted = true)
        val api = booksApi { search, page ->
            assertEquals(recentlyReadBooksSearch(), search)
            assertEquals(KomgaSort.KomgaBooksSort.byReadDateDesc().orders,
                (page?.sort as? KomgaSort.KomgaBooksSort)?.orders)
            assertEquals(RECENTLY_READ_BOOKS_LIMIT, page?.size)
            pageOf(listOf(reading, unread, deleted, completed))
        }

        assertEquals(listOf(completed, reading), loadRecentlyReadBooks(api, null, emptyList()))
    }

    @Test fun localFoldersMergeByReadDateWithoutDuplicatingTheSelectedCatalogBook() = runTest {
        val primary = homeReadingBook("primary", 1_000)
        val local = homeReadingBook("local", 2_000)
        val libraryIds = listOf(KomgaLibraryId("owned-local-folder"))
        val remote = booksApi { _, _ -> pageOf(listOf(primary)) }
        val offline = booksApi { search, _ ->
            assertEquals(localBookSearch(recentlyReadBooksSearch(), libraryIds), search)
            pageOf(listOf(local, primary))
        }

        assertEquals(listOf(local, primary), loadRecentlyReadBooks(remote, offline, libraryIds))
        assertEquals(listOf(primary), loadRecentlyReadBooks(remote,
            booksApi { _, _ -> error("No local library may query another account's cache") }, emptyList()))
    }

    @Test fun requestFailuresAndCancellationNeverBecomeEmptyReadingHistory() = runTest {
        assertFailsWith<IllegalStateException> {
            loadRecentlyReadBooks(booksApi { _, _ -> throw IllegalStateException("Synthetic catalog failure") }, null, emptyList())
        }
        assertFailsWith<CancellationException> {
            loadRecentlyReadBooks(booksApi { _, _ -> throw CancellationException("Retired reading session") }, null, emptyList())
        }
    }

    @Test fun returningHomeReloadsReadingHistoryWithoutAnSseEventOrConfiguredGroup() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val first = homeReadingBook("before", 1_000)
        val latest = homeReadingBook("after", 2_000)
        var savedBooks = listOf(first)
        val holder = "recent-home-${java.util.UUID.randomUUID()}"
        val vm = ScreenModelStore.getOrPut(holder, null) {
            HomeViewModel(
                seriesApi = stub { error("No configured series groups") },
                bookApi = booksApi { _, _ -> pageOf(savedBooks) },
                offlineSeriesApi = null,
                offlineBookApi = null,
                appNotifications = AppNotifications(),
                komgaEvents = MutableSharedFlow(),
                filterRepository = stub { flowOf(emptyList<HomeScreenFilter>()) },
                taskEmitter = null,
                localLibraryManager = null,
                cardWidthFlow = flowOf(240.dp),
            )
        }
        try {
            vm.initialize()
            advanceUntilIdle()
            assertEquals(listOf(first), vm.recentlyReadBooks.value)
            savedBooks = listOf(latest, first)
            vm.stopKomgaEventsHandler()
            vm.startKomgaEventsHandler()
            vm.initialize()
            advanceUntilIdle()
            assertEquals(listOf(latest, first), vm.recentlyReadBooks.value)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private fun pageOf(books: List<KomeliaBook>): Page<KomeliaBook> = Page.page(
        books,
        Pageable(Sort(sorted = true, unsorted = false, empty = books.isEmpty()),
            pageNumber = 0, pageSize = RECENTLY_READ_BOOKS_LIMIT, offset = 0, paged = true, unpaged = false),
        books.size,
    )

    private fun booksApi(answer: (KomgaBookSearch, KomgaPageRequest?) -> Page<KomeliaBook>): KomgaBookApi =
        Proxy.newProxyInstance(KomgaBookApi::class.java.classLoader, arrayOf(KomgaBookApi::class.java)) { _, method, arguments ->
            check(method.name == "getBookList") { "Unexpected catalog operation ${method.name}" }
            answer(arguments[0] as KomgaBookSearch, arguments[1] as KomgaPageRequest?)
        } as KomgaBookApi

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ -> answer(method.name) } as T
}
