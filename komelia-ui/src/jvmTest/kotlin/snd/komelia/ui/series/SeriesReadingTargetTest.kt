package snd.komelia.ui.series

import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.home.homeReadingBook
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.Page
import snd.komga.client.common.Pageable
import snd.komga.client.common.Sort
import snd.komga.client.search.BookConditionBuilder
import snd.komga.client.search.allOfBooks
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SeriesReadingTargetTest {
    private val seriesId = KomgaSeriesId("synthetic-series")

    @Test fun resumeUsesTheLatestInProgressBookAcrossTheWholeSeries() = runTest {
        val reading = homeReadingBook("volume-38").copy(seriesId = seriesId, number = 38)
        val calls = mutableListOf<BookConditionBuilder>()
        val api = booksApi { condition, page ->
            calls += condition
            assertEquals(0, page?.pageIndex)
            assertEquals(20, page?.size)
            assertEquals(KomgaSort.KomgaBooksSort.byReadDateDesc().orders,
                (page?.sort as? KomgaSort.KomgaBooksSort)?.orders)
            pageOf(listOf(reading))
        }
        assertEquals(reading, findSeriesReadingTarget(api, seriesId))
        assertEquals(listOf(condition(KomgaReadStatus.IN_PROGRESS).toBookCondition()), calls.map { it.toBookCondition() })
    }

    @Test fun aSeriesWithoutProgressStartsWithTheFirstUnreadVolume() = runTest {
        val unread = homeReadingBook("volume-12").copy(seriesId = seriesId, number = 12, readProgress = null)
        val calls = mutableListOf<BookConditionBuilder>()
        val api = booksApi { condition, page ->
            calls += condition
            if (calls.size == 1) pageOf(emptyList()) else {
                assertEquals(KomgaSort.KomgaBooksSort.byNumberAsc().orders,
                    (page?.sort as? KomgaSort.KomgaBooksSort)?.orders)
                pageOf(listOf(unread))
            }
        }
        assertEquals(unread, findSeriesReadingTarget(api, seriesId))
        assertEquals(listOf(KomgaReadStatus.IN_PROGRESS, KomgaReadStatus.UNREAD).map { condition(it).toBookCondition() },
            calls.map { it.toBookCondition() })
    }

    @Test fun anEmptySeriesHasNoReadingTarget() = runTest {
        assertNull(findSeriesReadingTarget(booksApi { _, _ -> pageOf(emptyList()) }, seriesId))
    }

    @Test fun aMissingRemoteVolumeDoesNotHideAnAvailableUnreadBook() = runTest {
        val unavailable = homeReadingBook("missing").copy(deleted = true, downloaded = false)
        val unread = homeReadingBook("unread").copy(readProgress = null)
        var requests = 0
        assertEquals(unread, findSeriesReadingTarget(booksApi { _, _ ->
            requests++
            pageOf(listOf(if (requests == 1) unavailable else unread))
        }, seriesId))
    }

    @Test fun errorsAndCancellationDoNotBecomeAnUnreadFallback() = runTest {
        assertFailsWith<IllegalStateException> {
            findSeriesReadingTarget(booksApi { _, _ -> error("Synthetic request failure") }, seriesId)
        }
        assertFailsWith<CancellationException> {
            findSeriesReadingTarget(booksApi { _, _ -> throw CancellationException() }, seriesId)
        }
    }

    private fun condition(status: KomgaReadStatus) = allOfBooks {
        seriesId { isEqualTo(this@SeriesReadingTargetTest.seriesId) }
        readStatus { isEqualTo(status) }
    }

    private fun pageOf(books: List<KomeliaBook>) = Page.page(books,
        Pageable(Sort(sorted = true, unsorted = false, empty = books.isEmpty()),
            pageNumber = 0, pageSize = 1, offset = 0, paged = true, unpaged = false), books.size)

    private fun booksApi(answer: (BookConditionBuilder, KomgaPageRequest?) -> Page<KomeliaBook>): KomgaBookApi =
        Proxy.newProxyInstance(KomgaBookApi::class.java.classLoader, arrayOf(KomgaBookApi::class.java)) { _, method, arguments ->
            check(method.name == "getBookList")
            answer(arguments[0] as BookConditionBuilder, arguments[2] as KomgaPageRequest?)
        } as KomgaBookApi
}
