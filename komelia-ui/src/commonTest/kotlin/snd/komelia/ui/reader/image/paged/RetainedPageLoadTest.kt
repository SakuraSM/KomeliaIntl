package snd.komelia.ui.reader.image.paged

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RetainedPageLoadTest {
    @Test
    fun navigatingAwayDoesNotCancelPrefetchedImage() = runTest {
        val ready = CompletableDeferred<Unit>()
        var loads = 0
        var disposed = 0
        val cached = RetainedPageLoad(backgroundScope, { _: String -> disposed++ }) {
            loads++
            ready.await()
            "cropped page"
        }
        val firstTurn = launch { cached.deferred.await() }
        runCurrent()
        firstTurn.cancel()
        runCurrent()
        assertFalse(cached.deferred.isCancelled)
        ready.complete(Unit)
        assertEquals("cropped page", cached.deferred.await())
        assertEquals(1, loads)
        assertEquals(0, disposed)
        cached.close()
        cached.close()
        assertEquals(1, disposed)
    }

    @Test
    fun evictingAnInFlightPageCancelsAndReleasesALateResultOnce() = runTest {
        val ready = CompletableDeferred<Unit>()
        var disposed = 0
        val cached = RetainedPageLoad(backgroundScope, { _: String -> disposed++ }) {
            withContext(NonCancellable) { ready.await() }
            "late image"
        }
        runCurrent()
        cached.close()
        ready.complete(Unit)
        runCurrent()
        assertTrue(cached.deferred.isCancelled)
        assertEquals(1, disposed)
        cached.close()
        assertEquals(1, disposed)
    }

    @Test
    fun preloadWindowIncludesTwoSpreadsEitherSideWithinTenPageBudget() {
        assertEquals(0..2, spreadPreloadRange(0, 20))
        assertEquals(3..7, spreadPreloadRange(5, 20))
        assertEquals(17..19, spreadPreloadRange(19, 20))
        assertEquals(0..0, spreadPreloadRange(0, 1))
        for (index in 0 until 20) {
            assertTrue(spreadPreloadRange(index, 20).count() * 2 <= 10)
        }
    }

    @Test
    fun movingWindowReusesNeighborAndReleasesOnlyEvictedPages() = runTest {
        val disposed = mutableListOf<Int>()
        val cache = RetainedPageCache<Int, Int>(backgroundScope, disposed::add)
        cache.retain((0..4).toSet())
        val loads = (0..4).associateWith { page -> cache.getOrLoad(page) { page } }
        loads.values.forEach { it.await() }
        cache.retain((1..5).toSet())
        assertEquals(listOf(0), disposed)
        assertSame(loads[2], cache.getOrLoad(2) { error("Neighbor must not be reloaded") })
        assertEquals(5, cache.getOrLoad(5) { 5 }.await())
        cache.close()
        cache.close()
        assertEquals((0..5).toList(), disposed.sorted())
        assertFailsWith<CancellationException> { cache.getOrLoad(3) { 3 } }
        assertFailsWith<CancellationException> { cache.retain(setOf(3)) }
    }

    @Test
    fun stoppingWindowCancelsPendingLoadsAndCannotBeRepopulated() = runTest {
        val cache = RetainedPageCache<Int, Int>(backgroundScope) { error("No image was created") }
        cache.retain(setOf(1))
        val pending = cache.getOrLoad(1) { CompletableDeferred<Int>().await() }
        runCurrent()
        cache.close()
        assertTrue(pending.isCancelled)
        assertFailsWith<CancellationException> { cache.getOrLoad(1) { 1 } }
    }

    @Test
    fun failedResultCanBeRetriedWithoutReloadingSuccessfulNeighbors() = runTest {
        val disposed = mutableListOf<String>()
        val cache = RetainedPageCache<Int, String>(backgroundScope, disposed::add)
        cache.retain(setOf(1, 2))
        assertEquals("error", cache.getOrLoad(1) { "error" }.await())
        val neighbor = cache.getOrLoad(2) { "image" }
        assertEquals("image", neighbor.await())
        assertEquals("recovered", cache.getOrLoad(1, acceptCached = { it != "error" }) { "recovered" }.await())
        assertSame(neighbor, cache.getOrLoad(2) { error("Already cached") })
        assertEquals(listOf("error"), disposed)
        cache.close()
    }
}
