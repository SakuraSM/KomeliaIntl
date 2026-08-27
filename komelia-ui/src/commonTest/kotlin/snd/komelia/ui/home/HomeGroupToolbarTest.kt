package snd.komelia.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.SeriesHomeScreenFilter

class HomeGroupToolbarTest {
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
}

private fun emptyBookGroup(order: Int, label: String) = BookFilterData(
    books = emptyList(),
    filter = BooksHomeScreenFilter.OnDeck(order = order, label = label, pageSize = 20),
)

private fun emptySeriesGroup(order: Int, label: String) = SeriesFilterData(
    series = emptyList(),
    filter = SeriesHomeScreenFilter.RecentlyAdded(order = order, label = label, pageSize = 20),
)
