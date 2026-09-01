package snd.komelia.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.KomgaSearchCondition
import snd.komga.client.series.KomgaSeriesSearch

class HomeMergedContentTest {
    @Test
    fun remoteAndLocalItemsAreMergedDeduplicatedSortedAndLimited() {
        val merged = mergeHomeItems(
            remote = listOf(9, 7, 5),
            local = listOf(8, 7, 6),
            limit = 4,
            idOf = { it },
            comparator = compareByDescending { it },
        )

        assertEquals(listOf(9, 8, 7, 6), merged)
    }

    @Test
    fun configuredBookFilterIsCombinedWithLocalLibraryScope() {
        val search = localBookSearch(
            KomgaBookSearch(),
            listOf(KomgaLibraryId("local-a"), KomgaLibraryId("local-b")),
        )

        val condition = search?.condition as KomgaSearchCondition.AnyOfBook
        assertEquals(2, condition.conditions.size)
    }

    @Test
    fun configuredSeriesFilterIsCombinedWithLocalLibraryScope() {
        val existing = KomgaSearchCondition.AllOfSeries(emptyList())
        val search = localSeriesSearch(
            KomgaSeriesSearch(condition = existing),
            listOf(KomgaLibraryId("local-a")),
        )

        val condition = search?.condition as KomgaSearchCondition.AllOfSeries
        assertEquals(2, condition.conditions.size)
    }

    @Test
    fun noLocalLibrariesSkipsTheOfflineQuery() {
        assertEquals(null, localBookSearch(KomgaBookSearch(), emptyList()))
        assertEquals(null, localSeriesSearch(KomgaSeriesSearch(), emptyList()))
    }
}
