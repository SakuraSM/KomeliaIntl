package snd.komelia.ui.reader.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.reader.image.continuous.continuousViewportChanges
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContinuousViewportChangesTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun jumpingToAnotherPageWithTheSameOffsetRequestsNewVisiblePixels() {
        val fixture = mount()
        val before = fixture.changes.get()
        compose.runOnIdle { fixture.scope.launch { fixture.list.scrollToItem(3) } }
        compose.waitUntil { fixture.list.firstVisibleItemIndex == 3 }
        compose.runOnIdle {
            assertEquals(0, fixture.list.firstVisibleItemScrollOffset)
            assertTrue(fixture.changes.get() > before, "A page-index change must refresh the visible image window")
        }
        val after = fixture.changes.get()
        compose.runOnIdle { fixture.scope.launch { fixture.list.scrollToItem(3) } }
        compose.waitForIdle()
        assertEquals(after, fixture.changes.get(), "An unchanged viewport must not create a refresh loop")
    }

    @Test
    fun pageRemeasurementRefreshesTheViewportWithoutScrolling() {
        val rowHeight = mutableStateOf(100.dp)
        val fixture = mount { rowHeight.value }
        val before = fixture.changes.get()
        compose.runOnIdle { rowHeight.value = 80.dp }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, fixture.list.firstVisibleItemIndex)
            assertEquals(0, fixture.list.firstVisibleItemScrollOffset)
            assertTrue(fixture.changes.get() > before, "A resized page changes which image regions are visible")
        }
    }

    private fun mount(rowHeight: () -> Dp = { 100.dp }): Fixture {
        val fixture = Fixture()
        compose.setContent {
            fixture.scope = rememberCoroutineScope()
            LaunchedEffect(fixture.list) {
                continuousViewportChanges(fixture.list).collect { fixture.changes.incrementAndGet() }
            }
            LazyColumn(state = fixture.list, modifier = Modifier.size(200.dp)) {
                items(20, key = { "page-$it" }) {
                    Box(Modifier.fillMaxWidth().height(rowHeight()))
                }
            }
        }
        compose.waitForIdle()
        return fixture
    }

    private class Fixture {
        val list = LazyListState(0, 0)
        val changes = AtomicInteger()
        lateinit var scope: CoroutineScope
    }
}
