package snd.komelia.ui.local

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import snd.komelia.ui.LoadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalContentRefreshCoordinatorTest {
    @Test
    fun refreshScansBeforeReloadingTheVisibleIndex() = runTest {
        val calls = mutableListOf<String>()
        val coordinator = LocalContentRefreshCoordinator(
            scanSources = { calls += "scan" },
            reloadIndex = { calls += "reload" },
        )

        assertTrue(coordinator.refresh { calls += "start" })
        assertEquals(listOf("start", "scan", "reload"), calls)
        assertFalse(coordinator.isRefreshing)
    }

    @Test
    fun concurrentPullGesturesDoNotQueueDuplicateScans() = runTest {
        val scanStarted = CompletableDeferred<Unit>()
        val finishScan = CompletableDeferred<Unit>()
        var scanCount = 0
        var reloadCount = 0
        val coordinator = LocalContentRefreshCoordinator(
            scanSources = {
                scanCount++
                scanStarted.complete(Unit)
                finishScan.await()
            },
            reloadIndex = { reloadCount++ },
        )

        val first = async { coordinator.refresh() }
        scanStarted.await()
        assertFalse(coordinator.refresh())
        finishScan.complete(Unit)

        assertTrue(first.await())
        assertEquals(1, scanCount)
        assertEquals(1, reloadCount)
    }

    @Test
    fun failedScanKeepsTheGateReusableAndDoesNotReload() = runTest {
        var shouldFail = true
        var reloadCount = 0
        val coordinator = LocalContentRefreshCoordinator(
            scanSources = {
                if (shouldFail) error("scan failed")
            },
            reloadIndex = { reloadCount++ },
        )

        assertFailsWith<IllegalStateException> { coordinator.refresh() }
        assertFalse(coordinator.isRefreshing)
        assertEquals(0, reloadCount)

        shouldFail = false
        assertTrue(coordinator.refresh())
        assertEquals(1, reloadCount)
    }

    @Test
    fun failedRefreshKeepsPreviouslyLoadedResultsVisible() {
        val previous = LoadState.Success(Unit)

        assertSame(previous, localRefreshFailureState(previous, IllegalStateException("scan failed")))
    }

    @Test
    fun failedInitialRefreshStillShowsTheErrorState() {
        val error = IllegalStateException("scan failed")

        val state = assertIs<LoadState.Error>(localRefreshFailureState(LoadState.Uninitialized, error))
        assertSame(error, state.exception)
    }
}
