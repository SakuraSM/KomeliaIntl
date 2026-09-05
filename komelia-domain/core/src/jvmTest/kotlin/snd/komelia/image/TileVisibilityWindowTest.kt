package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileVisibilityWindowTest {
    @Test fun panningDoesNotGrowPrefetchArea() {
        val origin = tileVisibilityWindow(Rect(0f, 0f, 100f, 200f))
        val panned = tileVisibilityWindow(Rect(10000f, 20000f, 10100f, 20200f))
        assertEquals(150f, origin.width)
        assertEquals(300f, origin.height)
        assertEquals(origin.size, panned.size)
        assertTrue(panned.left < 10000f && panned.right > 10100f)
        assertTrue(panned.top < 20000f && panned.bottom > 20200f)
    }
}
