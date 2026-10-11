package snd.komelia.image

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VisibleImageRequestsTest {
    @Test fun aBusyRendererReceivesTheFinalViewportAfterRapidChanges() = runTest {
        val requests = VisibleImageRequests<String>()
        val release = CompletableDeferred<Unit>()
        val rendered = mutableListOf<String>()
        val collector = backgroundScope.launch {
            requests.state.filterNotNull().collect {
                if (rendered.isEmpty()) release.await()
                rendered += it.request
            }
        }
        requests.submit("first")
        runCurrent()
        requests.submit("middle")
        requests.submit("last")
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "last"), rendered)
        assertEquals("last", requests.value?.request)
        assertEquals(3L, requests.serial)
        collector.cancel()
    }

    @Test fun returningToAnEarlierViewportDoesNotReuseItsInFlightVersion() {
        val requests = VisibleImageRequests<String>()
        requests.submit("first")
        val initial = requests.value
        requests.submit("middle")
        requests.submit("first")
        assertEquals(initial?.request, requests.value?.request)
        assertNotEquals(initial, requests.value)
        assertEquals(3L, requests.serial)
    }

    @Test fun unchangedGeometryIsDeduplicatedButAnExplicitReloadGetsANewVersion() {
        val requests = VisibleImageRequests<String>()
        requests.submit("first")
        val initial = requests.value
        requests.submit("first")
        assertEquals(initial, requests.value)
        requests.submit("first", force = true)
        assertEquals(initial?.request, requests.value?.request)
        assertEquals(2L, requests.serial)
    }

    @Test fun reloadDoesNotReplaceANewerViewportWithThePreviouslyRenderedOne() {
        val requests = VisibleImageRequests<String>()
        requests.reload(null)
        assertEquals(null, requests.value)
        requests.submit("previous")
        requests.submit("current")
        requests.reload("previous")
        assertEquals("current", requests.value?.request)
        assertEquals(3L, requests.serial)
    }
}
