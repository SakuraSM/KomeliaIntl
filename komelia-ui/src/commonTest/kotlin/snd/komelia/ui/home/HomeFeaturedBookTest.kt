package snd.komelia.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.common.cards.bookReadProgressFraction
import snd.komga.client.book.KomgaBookId

class HomeFeaturedBookTest {
    @Test
    fun heroUsesTheMostRecentlyReadBookAcrossVisibleGroupsWithoutReorderingThem() {
        val first = book("first")
        val second = book("second").let { it.copy(readProgress = it.readProgress!!.copy(readDate = Instant.fromEpochMilliseconds(2_000))) }
        val groups = listOf(group(3, listOf(second)), group(1, listOf(first, second)))

        assertEquals(HomeFeaturedBook(1, second), homeFeaturedBook(groups, HOME_ALL_TAB_ID))
        assertEquals(listOf(first, second), (groups.last()).books)
    }

    @Test
    fun selectedGroupCannotBorrowAReadingBookFromAnotherGroup() {
        val groups = listOf(group(1, listOf(book("reading"))), group(2, listOf(book("unread").copy(readProgress = null))))

        assertNull(homeFeaturedBook(groups, 2))
        assertEquals("reading", homeFeaturedBook(groups, 1)?.book?.id?.value)
        assertNull(homeFeaturedBook(groups, 99))
    }

    @Test
    fun completedDeletedAndUnreadBooksAreNotFeatured() {
        val reading = book("reading")
        val groups = listOf(group(1, listOf(
            reading.copy(id = KomgaBookId("completed"), readProgress = reading.readProgress!!.copy(completed = true)),
            reading.copy(id = KomgaBookId("deleted"), deleted = true),
            reading.copy(id = KomgaBookId("unread"), readProgress = null),
            reading,
        )))

        assertEquals(reading, homeFeaturedBook(groups, HOME_ALL_TAB_ID)?.book)
        assertNull(homeFeaturedBook(emptyList(), HOME_ALL_TAB_ID))
    }

    @Test
    fun unknownPageCountKeepsAStartedBookAvailableForContinueReading() {
        val reading = book("reading")
        val zero = reading.copy(id = KomgaBookId("zero"), readProgress = reading.readProgress!!.copy(page = 0))
        val unknown = reading.copy(id = KomgaBookId("unknown"), media = reading.media.copy(pagesCount = 0))
        assertEquals(unknown, homeFeaturedBook(listOf(group(1, listOf(zero, unknown))), HOME_ALL_TAB_ID)?.book)
        assertEquals(unknown, homeFeaturedBook(listOf(group(1, listOf(zero, unknown, reading))), HOME_ALL_TAB_ID)?.book)
        val longBook = reading.copy(media = reading.media.copy(pagesCount = 10000), readProgress = reading.readProgress!!.copy(page = 1))
        assertEquals(longBook, homeFeaturedBook(listOf(group(1, listOf(longBook))), HOME_ALL_TAB_ID)?.book)
    }

    @Test
    fun displayedProgressIsBoundedEvenWhenMetadataIsIncomplete() {
        val reading = book("reading")
        assertEquals(0.5f, bookReadProgressFraction(reading))
        assertEquals(0f, bookReadProgressFraction(reading.copy(readProgress = null)))
        assertNull(bookReadProgressFraction(reading.copy(media = reading.media.copy(pagesCount = 0))))
        assertEquals(1f, bookReadProgressFraction(reading.copy(readProgress = reading.readProgress!!.copy(completed = true))))
    }

    private fun group(order: Int, books: List<KomeliaBook>) = BookFilterData(
        books = books,
        filter = BooksHomeScreenFilter.OnDeck(order, "Custom group $order", 20),
    )

    private fun book(id: String): KomeliaBook = homeReadingBook(id)
}
