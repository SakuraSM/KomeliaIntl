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
        assertEquals(0, reconcileActiveHomeFilter(activeFilterNumber = 4, filterCount = 3))
        assertEquals(2, reconcileActiveHomeFilter(activeFilterNumber = 2, filterCount = 3))
    }
}
