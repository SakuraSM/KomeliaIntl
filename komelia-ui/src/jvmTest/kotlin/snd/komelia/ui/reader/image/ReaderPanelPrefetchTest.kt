package snd.komelia.ui.reader.image

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.junit.Test
import snd.komelia.image.*
import snd.komelia.image.processing.ImageProcessingPipeline
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ReaderPanelPrefetchTest {
    @Test fun nextViewportIsPreparedWithoutChangingCurrentPainterAndReusedOnSwitch() = runBlocking {
        val reader = ProbeReader()
        val budget = ReaderPrefetchBudget()
        try {
            reader.requestUpdate(area, 3f, first)
            val current = withTimeout(5000) { reader.painter.filterNotNull().first { (it as ProbePainter).sharpRegions.isNotEmpty() } }
            val before = reader.regions.get()
            reader.prefetch(ReaderImagePrefetch(listOf(ImageViewport(area, 3f, second)), budget))
            assertTrue(reader.regions.get() > before, "The next panel must already have rendered pixels before switching")
            assertSame(current, reader.painter.value, "Background rendering must not display the future panel")
            assertTrue(budget.usedBytes in 1..budget.maximumBytes)
            val rendered = reader.regions.get()
            reader.holdRegions = true
            // Animation and prediction can differ by one Float ULP but produce the same pixels.
            reader.requestUpdate(area, Math.nextUp(3f), second)
            withTimeout(5000) { reader.painter.filterNotNull().first { it !== current } }
            assertEquals(rendered, reader.regions.get(), "A prepared target must not rerender its tiles")
        } finally {
            reader.release.complete(Unit)
            reader.close()
            withTimeout(5000) { reader.painter.first { it == null } }
            assertEquals(0, budget.usedBytes)
            assertTrue(reader.created.all { it.isClosed })
        }
    }

    @Test fun tinySharedBudgetSkipsSpeculativePixelsAndLeavesVisibleContentUsable() = runBlocking {
        val reader = ProbeReader()
        val budget = ReaderPrefetchBudget(1)
        try {
            reader.requestUpdate(area, 3f, first)
            val current = withTimeout(5000) { reader.painter.filterNotNull().first { (it as ProbePainter).sharpRegions.isNotEmpty() } }
            val before = reader.regions.get()
            reader.prefetch(ReaderImagePrefetch(listOf(ImageViewport(area, 3f, second)), budget))
            assertEquals(before, reader.regions.get())
            assertSame(current, reader.painter.value)
            assertEquals(0, budget.usedBytes)
        } finally { reader.close() }
    }

    @Test fun clearingPreparedPixelsReleasesBudgetAndForcesFreshRendering() = runBlocking {
        val reader = ProbeReader()
        val budget = ReaderPrefetchBudget()
        try {
            reader.requestUpdate(area, 3f, first)
            val current = withTimeout(5000) { reader.painter.filterNotNull().first { (it as ProbePainter).sharpRegions.isNotEmpty() } }
            reader.prefetch(ReaderImagePrefetch(listOf(ImageViewport(area, 3f, second)), budget))
            assertTrue(budget.usedBytes > 0)
            reader.clearPrefetch()
            withTimeout(5000) { while (budget.usedBytes != 0L) delay(1) }
            val before = reader.regions.get()
            reader.requestUpdate(area, 3f, second)
            withTimeout(5000) { reader.painter.filterNotNull().first { it !== current } }
            assertTrue(reader.regions.get() > before)
        } finally { reader.close() }
    }

    @Test fun newVisibleRequestInterruptsSpeculativeWorkAfterTheCurrentNativeOperation() = runBlocking {
        val reader = ProbeReader()
        val budget = ReaderPrefetchBudget()
        try {
            reader.requestUpdate(area, 3f, first)
            val current = withTimeout(5000) { reader.painter.filterNotNull().first { (it as ProbePainter).sharpRegions.isNotEmpty() } }
            val before = reader.regions.get()
            reader.holdRegions = true
            val prefetch = async { reader.prefetch(ReaderImagePrefetch(listOf(ImageViewport(area, 3f, second)), budget)) }
            withTimeout(5000) { while (reader.regions.get() == before) delay(1) }
            reader.requestUpdate(area, 4f, first)
            reader.release.complete(Unit)
            assertEquals(0, withTimeout(5000) { prefetch.await() })
            withTimeout(5000) { reader.painter.filterNotNull().first { it !== current } }
            assertEquals(0L, budget.usedBytes)
            assertNull(reader.error.value)
        } finally { reader.release.complete(Unit); reader.close() }
    }

    @Test fun sharedBudgetCannotBeOvercommittedAndReleaseIsIdempotent() {
        val budget = ReaderPrefetchBudget(100)
        val firstLease = assertNotNull(budget.reserve(60))
        assertNull(budget.reserve(50))
        val secondLease = assertNotNull(budget.reserve(40))
        assertEquals(100L, budget.usedBytes)
        firstLease.release()
        firstLease.release()
        assertEquals(40L, budget.usedBytes)
        secondLease.release()
        assertEquals(0L, budget.usedBytes)
    }

    private class ProbePainter(val sharpRegions: List<Rect>, size: IntSize) : TilingReaderImage.TiledPainter() {
        override val intrinsicSize = Size(size.width.toFloat(), size.height.toFloat())
        override fun DrawScope.onDraw() = Unit
        override fun withSamplingMode(upsamplingMode: UpsamplingMode) = this
    }

    private class ProbeReader : TilingReaderImage(
        ImageSource.MemorySource(byteArrayOf()), Decoder, ImageProcessingPipeline(),
        MutableStateFlow(true), MutableStateFlow(UpsamplingMode.BILINEAR),
        MutableStateFlow(ReduceKernel.MITCHELL), MutableStateFlow(false), ReaderImage.PageId("panel-prefetch-test", 1),
    ) {
        val regions = AtomicInteger()
        val created = CopyOnWriteArrayList<Image>()
        @Volatile var holdRegions = false
        val release = CompletableDeferred<Unit>()
        override suspend fun resizeImage(image: KomeliaImage, scaleWidth: Int, scaleHeight: Int) = pixels(scaleWidth, scaleHeight)
        override suspend fun getImageRegion(image: KomeliaImage, imageRegion: IntRect, scaleWidth: Int, scaleHeight: Int): ReaderImageData {
            regions.incrementAndGet()
            if (holdRegions) release.await()
            return pixels(scaleWidth, scaleHeight)
        }
        private fun pixels(width: Int, height: Int): ReaderImageData {
            val surface = Surface.makeRasterN32Premul(1, 1)
            val bitmap = surface.makeImageSnapshot()
            surface.close()
            created.add(bitmap)
            return ReaderImageData(width, height, listOf(bitmap), null)
        }
        override fun createTilePainter(tiles: List<ReaderImageTile>, displaySize: IntSize, scaleFactor: Double) =
            ProbePainter(tiles.filterNot { it.isFallback }.map { it.displayRegion }, displaySize)
        override fun closeTileBitmaps(tiles: List<ReaderImageTile>) { tiles.forEach { it.renderImage?.close() } }
    }

    private object Decoder : KomeliaImageDecoder {
        override suspend fun decode(encoded: ByteArray, nPages: Int?) = TestImage()
        override suspend fun decodeFromFile(path: String, nPages: Int?) = TestImage()
        override suspend fun decodeAndResize(encoded: ByteArray, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?) = TestImage()
        override suspend fun decodeAndResize(path: String, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?) = TestImage()
    }

    private class TestImage : KomeliaImage {
        override val width = 1988
        override val height = 3057
        override val bands = 4
        override val type = ImageFormat.RGBA_8888
        override val pagesLoaded = 1
        override val pagesTotal = 1
        override val pageHeight = height
        override val pageDelays: IntArray? = null
        override suspend fun extractArea(rect: ImageRect): KomeliaImage = error("unused")
        override suspend fun resize(scaleWidth: Int, scaleHeight: Int, linear: Boolean, kernel: ReduceKernel): KomeliaImage = error("unused")
        override suspend fun shrink(factor: Double): KomeliaImage = error("unused")
        override suspend fun findTrim(): ImageRect = error("unused")
        override suspend fun makeHistogram(): KomeliaImage = error("unused")
        override suspend fun mapLookupTable(table: ByteArray): KomeliaImage = error("unused")
        override suspend fun getBytes(): ByteArray = error("unused")
        override fun close() = Unit
    }

    private companion object {
        val area = IntSize(720, 1280)
        val first = IntRect(0, 0, 240, 426)
        val second = IntRect(450, 650, 690, 1076)
    }
}
