package snd.komelia

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import snd.komelia.image.AndroidReaderImage
import snd.komelia.image.ImageSource
import snd.komelia.image.KomeliaImage
import snd.komelia.image.KomeliaImageDecoder
import snd.komelia.image.ReaderImage
import snd.komelia.image.ReduceKernel
import snd.komelia.image.TilingReaderImage.ReaderImageTile
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.processing.ImageProcessingPipeline

class AndroidReaderImageLifetimeTest {
    @Test
    fun fallbackFillsGapsWithoutDoublingTransparentTileAlpha() {
        val reader = AndroidReaderImage(
            SuspendedDecoder, ImageSource.MemorySource(byteArrayOf()), ImageProcessingPipeline(),
            MutableStateFlow(false), ReaderImage.PageId("fallback-pixels-test", 1),
            MutableStateFlow(UpsamplingMode.NEAREST), MutableStateFlow(ReduceKernel.NEAREST),
            MutableStateFlow(false),
        )
        val base = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(0x80ff0000.toInt()) }
        val detail = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(0x80ff0000.toInt()) }
        val output = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        try {
            val tiles = listOf(
                ReaderImageTile(IntSize(1, 1), Rect(0f, 0f, 100f, 100f), true, base, isFallback = true),
                ReaderImageTile(IntSize(1, 1), Rect(0f, 0f, 50f, 100f), true, detail),
            )
            // IntSize is an inline Long on Android; invoke the existing production painter factory.
            val factory = AndroidReaderImage::class.java.declaredMethods.first { it.name.startsWith("createTilePainter") }
            factory.isAccessible = true
            val painter = factory.invoke(reader, tiles, (100L shl 32) or 100L, 1.0) as Painter
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(output.asImageBitmap()), Size(100f, 100f)
            ) { with(painter) { draw(Size(100f, 100f)) } }
            assertEquals("High-resolution region must not composite the preview twice", 128, android.graphics.Color.alpha(output.getPixel(25, 50)))
            assertEquals("Unloaded region must still contain preview pixels", 128, android.graphics.Color.alpha(output.getPixel(75, 50)))
            assertEquals(output.getPixel(25, 50), output.getPixel(75, 50))
        } finally {
            reader.close()
            base.recycle()
            detail.recycle()
            output.recycle()
        }
    }

    @Test
    fun retiredTilesRemainDrawableByAnOutgoingFrame() {
        val reader = AndroidReaderImage(
            imageDecoder = SuspendedDecoder,
            imageSource = ImageSource.MemorySource(byteArrayOf()),
            processingPipeline = ImageProcessingPipeline(),
            stretchImages = MutableStateFlow(false),
            pageId = ReaderImage.PageId("bitmap-lifetime-test", 1),
            upsamplingMode = MutableStateFlow(UpsamplingMode.NEAREST),
            downSamplingKernel = MutableStateFlow(ReduceKernel.LANCZOS3),
            linearLightDownSampling = MutableStateFlow(false),
        )
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        val tiles = listOf(ReaderImageTile(IntSize(8, 8), Rect(0f, 0f, 8f, 8f), true, bitmap))
        val output = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            // Exercise the same retirement hook used by resize, tile replacement and close.
            val retire = AndroidReaderImage::class.java.getDeclaredMethod("closeTileBitmaps", List::class.java)
            retire.isAccessible = true
            retire.invoke(reader, tiles)
            reader.close()
            assertFalse("An outgoing frame still owns this bitmap", bitmap.isRecycled)
            Canvas(output).drawBitmap(bitmap, 0f, 0f, null)
            assertEquals(android.graphics.Color.RED, output.getPixel(4, 4))
        } finally {
            reader.close()
            bitmap.recycle()
            output.recycle()
        }
    }

    private object SuspendedDecoder : KomeliaImageDecoder {
        override suspend fun decode(encoded: ByteArray, nPages: Int?): KomeliaImage = awaitCancellation()
        override suspend fun decodeFromFile(path: String, nPages: Int?): KomeliaImage = awaitCancellation()
        override suspend fun decodeAndResize(encoded: ByteArray, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?): KomeliaImage = awaitCancellation()
        override suspend fun decodeAndResize(path: String, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?): KomeliaImage = awaitCancellation()
    }
}
