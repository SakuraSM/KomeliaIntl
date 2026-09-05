package snd.komelia.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.SeriesHomeScreenFilter

@OptIn(ExperimentalCoroutinesApi::class)
class HomeConfigurationRefreshTest {
    @Test
    fun repositoryChangesAndManualReloadsUseTheLatestConfiguration() = runTest {
        val configurations = MutableStateFlow(listOf("continue-reading"))
        val reloads = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val observed = mutableListOf<List<String>>()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            homeConfigurationRefreshFlow(configurations, reloads)
                .take(3)
                .toList(observed)
        }

        runCurrent()
        configurations.value = listOf("recent", "continue-reading")
        runCurrent()
        reloads.emit(Unit)
        runCurrent()

        assertEquals(
            listOf(
                listOf("continue-reading"),
                listOf("recent", "continue-reading"),
                listOf("recent", "continue-reading"),
            ),
            observed,
        )
        collectJob.cancel()
    }

    @Test
    fun removedActiveGroupFallsBackToAll() {
        assertEquals(
            0,
            reconcileActiveHomeFilter(
                activeFilterNumber = 4,
                availableFilterNumbers = listOf(1, 2, 3),
            ),
        )
        assertEquals(
            2,
            reconcileActiveHomeFilter(
                activeFilterNumber = 2,
                availableFilterNumbers = listOf(1, 2, 3),
            ),
        )
    }

    @Test
    fun activeGroupUsesPersistedOrderInsteadOfListIndex() {
        assertEquals(
            7,
            reconcileActiveHomeFilter(
                activeFilterNumber = 7,
                availableFilterNumbers = listOf(2, 7, 11),
            ),
        )
        assertEquals(
            0,
            reconcileActiveHomeFilter(
                activeFilterNumber = 3,
                availableFilterNumbers = listOf(2, 7, 11),
            ),
        )
    }

    @Test
    fun homeContentUsesPersistedOrderAcrossRepositoryImplementations() {
        val filters = listOf(
            SeriesHomeScreenFilter.RecentlyAdded(order = 3, label = "Third", pageSize = 20),
            BooksHomeScreenFilter.OnDeck(order = 1, label = "First", pageSize = 20),
            SeriesHomeScreenFilter.RecentlyUpdated(order = 2, label = "Second", pageSize = 20),
        )

        assertEquals(
            listOf("First", "Second", "Third"),
            orderedHomeScreenFilters(filters).map { it.label },
        )
    }
}
