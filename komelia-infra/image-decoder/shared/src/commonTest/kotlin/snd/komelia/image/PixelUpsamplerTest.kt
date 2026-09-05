package snd.komelia.image

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PixelUpsamplerTest {
    private val kernels = listOf(ReduceKernel.MITCHELL, ReduceKernel.LANCZOS3)

    @Test fun preservesConstantColorIncludingBordersAndOnePixelImages() = runTest {
        for (kernel in kernels) {
            val color = byteArrayOf(31, 97, 203.toByte(), 255.toByte())
            val result = upscalePixels(color, 1, 1, 4, 17, 13, kernel)
            assertTrue(result.all { it == 0xff1f61cb.toInt() })
        }
    }

    @Test fun mitchellUsesTheBAndCOneThirdReconstructionKernel() = runTest {
        val impulse = byteArrayOf(0, 0, 255.toByte(), 0, 0)
        val result = upscalePixels(impulse, 5, 1, 1, 15, 1, ReduceKernel.MITCHELL)
        // At source pixel centers the Mitchell kernel is 8/9 at zero and 1/18 at +/-1.
        assertEquals(227, result[7] and 255)
        assertEquals(14, result[4] and 255)
        assertEquals(14, result[10] and 255)
        assertEquals(0, result[1] and 255)
        assertEquals(0, result[13] and 255)
    }

    @Test fun lanczosInterpolatesOriginalSamplesAndHasThreeLobeSupport() = runTest {
        val impulse = ByteArray(9) { if (it == 4) 255.toByte() else 0 }
        val result = upscalePixels(impulse, 9, 1, 1, 27, 1, ReduceKernel.LANCZOS3)
        for (x in impulse.indices) assertEquals(impulse[x].toInt() and 255, result[x * 3 + 1] and 255)
        // Its outer positive lobe reaches beyond a cubic kernel's two-pixel support.
        assertTrue((result[6] and 255) > 0)
        assertEquals(0, result[3] and 255)
    }

    @Test fun transparentPixelsDoNotBleedTheirHiddenColor() = runTest {
        val rgba = byteArrayOf(255.toByte(), 0, 0, 255.toByte(), 0, 0, 255.toByte(), 0)
        for (kernel in kernels) {
            val result = upscalePixels(rgba, 2, 1, 4, 12, 3, kernel)
            assertTrue(result.any { (it ushr 24) in 1..254 })
            result.forEach { color ->
                if (color ushr 24 == 0) assertEquals(0, color)
                else assertEquals(0xff0000, color and 0xffffff)
            }
        }
    }

    @Test fun tileWithNeighborPixelsMatchesFullImageAtItsEdges() = runTest {
        val width = 32
        val height = 3
        val source = ByteArray(width * height) { ((it * 73 + 19) % 256).toByte() }
        val paddedTile = ByteArray(14 * height) { source[it / 14 * width + 5 + it % 14] }
        for (kernel in kernels) {
            val full = upscalePixels(source, width, height, 1, width * 3, height * 3, kernel)
            val tile = upscalePixels(paddedTile, 14, height, 1, 24, height * 3, kernel, ImageRect(3, 0, 11, height))
            for (y in 0 until height * 3) {
                assertContentEquals(full.copyOfRange(y * width * 3 + 24, y * width * 3 + 48), tile.copyOfRange(y * 24, (y + 1) * 24))
            }
        }
    }

    @Test fun acceptsRgbAndGrayAlphaDecodedImages() = runTest {
        val rgb = byteArrayOf(31, 97, 203.toByte())
        val grayAlpha = byteArrayOf(97, 127)
        for (kernel in kernels) {
            assertTrue(upscalePixels(rgb, 1, 1, 3, 3, 3, kernel).all { it == 0xff1f61cb.toInt() })
            assertTrue(upscalePixels(grayAlpha, 1, 1, 2, 3, 3, kernel).all { it == 0x7f616161 })
        }
    }

    @Test fun rejectsDownsamplingAndInvalidBuffers() = runTest {
        assertFailsWith<IllegalArgumentException> {
            upscalePixels(ByteArray(4), 2, 2, 1, 1, 1, ReduceKernel.LANCZOS3)
        }
        assertFailsWith<IllegalArgumentException> {
            upscalePixels(ByteArray(3), 2, 2, 1, 4, 4, ReduceKernel.MITCHELL)
        }
    }
}
