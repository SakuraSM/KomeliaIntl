package snd.komelia.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.search.allOfBooks

class HomeGroupToolbarTest {
    @Test fun allCountIncludesTheIndependentHistoryAndExcludesReplacedDefaultRows() {
        val reading = homeReadingBook("reading")
        val completed = homeReadingBook("completed").let { it.copy(readProgress = it.readProgress!!.copy(completed = true)) }
        val next = homeReadingBook("next").copy(readProgress = null)
        val groups = listOf(
            BookFilterData(listOf(reading), readingFilter()),
            BookFilterData(listOf(next), BooksHomeScreenFilter.OnDeck(2, "On deck", 20)),
        )
        assertEquals(3, homeAllItemCount(groups, listOf(completed, reading)))
        assertEquals(2, homeAllItemCount(groups, emptyList()))
    }

    @Test fun allCountIncludesAnOlderContinueReadingHeroOutsideTheRecentPage() {
        val older = homeReadingBook("older", 1_000)
        val completed = homeReadingBook("completed", 2_000).let { it.copy(readProgress = it.readProgress!!.copy(completed = true)) }
        assertEquals(2, homeAllItemCount(listOf(BookFilterData(listOf(older), readingFilter())), listOf(completed)))
    }

    @Test
    fun newlyConfiguredEmptyGroupRemainsVisible() {
        val groups = listOf(
            emptyBookGroup(order = 1, label = "Continue reading"),
            emptySeriesGroup(order = 2, label = "New group"),
        )

        assertEquals(
            listOf("Continue reading", "New group"),
            homeGroupToolbarFilters(groups).map { it.filter.label },
        )
    }

    @Test
    fun toolbarUsesPersistedGroupOrderInsteadOfRepositoryListOrder() {
        val groups = listOf(
            emptySeriesGroup(order = 3, label = "Third"),
            emptyBookGroup(order = 1, label = "First"),
            emptySeriesGroup(order = 2, label = "Second"),
        )

        assertEquals(
            listOf("First", "Second", "Third"),
            homeGroupToolbarFilters(groups).map { it.filter.label },
        )
    }

    @Test
    fun toolbarContainsOnlyConfiguredGroups() {
        val groups = listOf(
            emptySeriesGroup(order = 2, label = "Recently added"),
            emptyBookGroup(order = 1, label = "Continue reading"),
        )

        val entries = homeToolbarEntries(groups)

        assertEquals(listOf(1, 2), entries.map { it.id })
        assertEquals(listOf(0, 0), entries.map { it.itemCount })
    }

    @Test
    fun emptyConfigurationHasNoToolbarEntries() {
        assertEquals(emptyList(), homeToolbarEntries(emptyList()))
    }
}

private fun readingFilter() = BooksHomeScreenFilter.CustomFilter(
    order = 1, label = "Continue reading",
    filter = allOfBooks { readStatus { isEqualTo(KomgaReadStatus.IN_PROGRESS) } }.toBookCondition(),
    pageRequest = KomgaPageRequest(sort = KomgaSort.KomgaBooksSort.byReadDateDesc()),
)

private fun emptyBookGroup(order: Int, label: String) = BookFilterData(
    books = emptyList(),
    filter = BooksHomeScreenFilter.OnDeck(order = order, label = label, pageSize = 20),
)

private fun emptySeriesGroup(order: Int, label: String) = SeriesFilterData(
    series = emptyList(),
    filter = SeriesHomeScreenFilter.RecentlyAdded(order = order, label = label, pageSize = 20),
)
