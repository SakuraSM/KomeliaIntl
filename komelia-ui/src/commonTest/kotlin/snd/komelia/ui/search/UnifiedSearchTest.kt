package snd.komelia.ui.search

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnifiedSearchTest {
    @Test
    fun fetchSizeIncludesEveryItemNeededForRequestedPage() {
        assertEquals(10, searchFetchSize(1))
        assertEquals(30, searchFetchSize(3))
        assertEquals(10, searchFetchSize(0))
    }

    @Test
    fun remoteAndLocalResultsShareOneStablePage() {
        val result = mergeSearchPages(
            remote = SearchSourcePage(listOf(12, 10, 8, 6), totalElements = 4),
            local = SearchSourcePage(listOf(11, 9, 7, 5), totalElements = 4),
            pageNumber = 1,
            pageSize = 5,
            idOf = { it },
            comparator = compareByDescending { it },
        )

        assertEquals(listOf(12, 11, 10, 9, 8), result.content)
        assertEquals(1, result.currentPage)
        assertEquals(2, result.totalPages)
    }

    @Test
    fun secondPageContinuesAcrossBothSourcesWithoutRepeatingItems() {
        val result = mergeSearchPages(
            remote = SearchSourcePage(listOf(12, 10, 8, 6), totalElements = 4),
            local = SearchSourcePage(listOf(11, 9, 7, 5), totalElements = 4),
            pageNumber = 2,
            pageSize = 5,
            idOf = { it },
            comparator = compareByDescending { it },
        )

        assertEquals(listOf(7, 6, 5), result.content)
        assertEquals(2, result.currentPage)
        assertEquals(2, result.totalPages)
    }

    @Test
    fun duplicateCachedItemsAreRenderedOnlyOnce() {
        val result = mergeSearchPages(
            remote = SearchSourcePage(listOf(5, 4, 3), totalElements = 3),
            local = SearchSourcePage(listOf(4, 2, 1), totalElements = 3),
            pageNumber = 1,
            pageSize = 10,
            idOf = { it },
            comparator = compareByDescending { it },
        )

        assertEquals(listOf(5, 4, 3, 2, 1), result.content)
        assertEquals(1, result.totalPages)
    }

    @Test
    fun remoteFailureFallsBackToAllOfflineResults() = runTest {
        val result = loadUnifiedSearchPage(
            pageNumber = 1,
            idOf = { it },
            comparator = compareByDescending { it },
            remoteLoad = { error("server unavailable") },
            localLoad = { SearchSourcePage(listOf(2), 1) },
            offlineFallback = { SearchSourcePage(listOf(4, 3, 2), 3) },
        )

        assertEquals(SearchCoverage.OFFLINE_ONLY, result.coverage)
        assertEquals(listOf(4, 3, 2), result.page.content)
    }

    @Test
    fun localIndexFailureKeepsRemoteResults() = runTest {
        val result = loadUnifiedSearchPage(
            pageNumber = 1,
            idOf = { it },
            comparator = compareByDescending { it },
            remoteLoad = { SearchSourcePage(listOf(4, 3), 2) },
            localLoad = { error("local index unavailable") },
            offlineFallback = { SearchSourcePage(listOf(2, 1), 2) },
        )

        assertEquals(SearchCoverage.REMOTE_ONLY, result.coverage)
        assertEquals(listOf(4, 3), result.page.content)
    }

    @Test
    fun failureIsReportedWhenNeitherSourceCanSearch() = runTest {
        assertFailsWith<IllegalStateException> {
            loadUnifiedSearchPage<Int, Int>(
                pageNumber = 1,
                idOf = { it },
                comparator = compareByDescending { it },
                remoteLoad = { error("server unavailable") },
                localLoad = null,
                offlineFallback = { error("offline index unavailable") },
            )
        }
    }
}
