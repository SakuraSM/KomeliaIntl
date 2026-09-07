package snd.komelia.image

import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileFallbackSizeTest {
    @Test fun portraitUsesAtMostOneAndAHalfMiBOfRgbaPixels() {
        assertEquals(IntSize(512, 768), tileFallbackSize(IntSize(4000, 6000)))
    }

    @Test fun smallImagesAreNotUpscaled() {
        assertEquals(IntSize(100, 200), tileFallbackSize(IntSize(100, 200)))
    }

    @Test fun largeSquareAndExtremeAspectRatiosStayBounded() {
        for (size in listOf(IntSize(10000, 10000), IntSize(1, 100000), IntSize(100000, 1))) {
            val result = tileFallbackSize(size)
            assertTrue(result.width in 1..768 && result.height in 1..768)
            assertTrue(result.width.toLong() * result.height * 4 <= 768L * 768 * 4)
        }
    }
}
