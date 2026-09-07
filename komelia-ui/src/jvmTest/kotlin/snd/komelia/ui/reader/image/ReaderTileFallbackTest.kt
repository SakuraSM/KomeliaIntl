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
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image
import org.junit.Test
import snd.komelia.image.*
import snd.komelia.image.processing.ImageProcessingPipeline
import snd.komelia.image.processing.ProcessingStep
import kotlin.test.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import java.util.IdentityHashMap

/** Exercises real tile publication while native full-frame generation is suspended. */
class ReaderTileFallbackTest {
    @Test fun zoomOutRetainsWholePageCoverageUntilNextFrameIsReady() = verify(3f, true)
    @Test fun fullFrameControlKeepsCoverageDuringTheSameDelay() = verify(1.5f, true)

    @Test fun cropReloadReplacesThePreviewInsteadOfReusingOldPixels() = runBlocking {
        val pipeline = ImageProcessingPipeline()
        val step = ReloadStep()
        val registered = async(start = CoroutineStart.UNDISPATCHED) { pipeline.changeFlow.first() }
        pipeline.addStep(step)
        registered.await()
        val reader = ProbeReader(pipeline)
        try {
            reader.requestUpdate(IntSize(1000, 1500), 3f, IntRect(0, 0, 333, 500))
            val before = withTimeout(5000) { reader.painter.filterNotNull().first { reader.previewResizes.get() == 1 } }
            step.changed()
            withTimeout(5000) { reader.painter.filterNotNull().first { it !== before && reader.previewResizes.get() == 2 } }
            assertEquals(2, reader.previewResizes.get())
        } finally {
            reader.close()
            withTimeout(5000) { reader.painter.first { it == null } }
            assertTrue(reader.createdImages.all { it.isClosed })
        }
    }

    private class ReloadStep : ProcessingStep {
        lateinit var changed: () -> Unit
        override suspend fun process(pageId: ReaderImage.PageId, image: KomeliaImage) = TestImage()
        override suspend fun addChangeListener(callback: () -> Unit) { changed = callback }
    }

    private fun verify(initialZoom: Float, expectCoverageWhilePending: Boolean) = runBlocking {
        repeat(3) { iteration ->
            val reader = ProbeReader()
            try {
                val initialViewport = IntRect(0, 0, (1000 / initialZoom).toInt(), (1500 / initialZoom).toInt())
                reader.requestUpdate(IntSize(1000, 1500), initialZoom, initialViewport)
                val old = withTimeout(5000) { reader.painter.filterNotNull().first { initialZoom < 3f || (it as ProbePainter).regions.any { region -> region.width < 1000f } } } as ProbePainter
                assertTrue(old.covers(Rect(0f, 0f, initialViewport.right.toFloat(), initialViewport.bottom.toFloat())), "Initial panel must be covered")
                reader.holdFullResize = true
                reader.requestUpdate(IntSize(1000, 1500), 1f, IntRect(0, 0, 1000, 1500))
                withTimeout(5000) { reader.fullResizeStarted.await() }
                val pending = reader.painter.value as ProbePainter
                assertSame(old, pending)
                val covered = pending.covers(Rect(0f, 0f, 1000f, 1500f))
                assertEquals(expectCoverageWhilePending, covered)
                println("Tile fallback zoom=$initialZoom run=$iteration: pending tiles=${pending.regions}; whole-page coverage=$covered")
                reader.releaseFullResize.complete(Unit)
                val ready = withTimeout(5000) { reader.painter.filterNotNull().first { it !== old } } as ProbePainter
                assertTrue(ready.covers(Rect(0f, 0f, 1000f, 1500f)))
                assertEquals(if (initialZoom == 3f) 1 else 0, reader.previewResizes.get())
                if (initialZoom == 3f) {
                    reader.requestUpdate(IntSize(1000, 1500), 3f, IntRect(600, 900, 933, 1400))
                    withTimeout(5000) { reader.painter.filterNotNull().first { painter ->
                        (painter as ProbePainter).regions.any { it.left >= 512f }
                    } }
                    assertEquals(1, reader.previewResizes.get(), "Zoom and pan must reuse the same preview")
                }
            } finally {
                reader.releaseFullResize.complete(Unit)
                reader.close()
                withTimeout(5000) { reader.painter.first { it == null } }
                assertTrue(reader.createdImages.all { it.isClosed }, "Page close must release preview and tiles")
                val retiredIdentities = Collections.newSetFromMap(IdentityHashMap<Image, Boolean>())
                retiredIdentities.addAll(reader.retiredImages)
                assertEquals(reader.retiredImages.size, retiredIdentities.size, "Do not retire reused preview pixels twice")
            }
        }
    }

    private class ProbePainter(val regions: List<Rect>, size: IntSize) : TilingReaderImage.TiledPainter() {
        override val intrinsicSize = Size(size.width.toFloat(), size.height.toFloat())
        override fun DrawScope.onDraw() = Unit
        override fun withSamplingMode(upsamplingMode: UpsamplingMode) = this
        fun covers(rect: Rect): Boolean {
            val xs = (listOf(rect.left, rect.right) + regions.flatMap { listOf(it.left, it.right) })
                .filter { it in rect.left..rect.right }.distinct().sorted()
            val ys = (listOf(rect.top, rect.bottom) + regions.flatMap { listOf(it.top, it.bottom) })
                .filter { it in rect.top..rect.bottom }.distinct().sorted()
            return xs.zipWithNext().all { (left, right) ->
                ys.zipWithNext().all { (top, bottom) ->
                    val x = (left + right) / 2
                    val y = (top + bottom) / 2
                    regions.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }
                }
            }
        }
    }

    private class ProbeReader(pipeline: ImageProcessingPipeline = ImageProcessingPipeline()) : TilingReaderImage(
        ImageSource.MemorySource(byteArrayOf()), Decoder, pipeline,
        MutableStateFlow(true), MutableStateFlow(UpsamplingMode.NEAREST),
        MutableStateFlow(ReduceKernel.NEAREST), MutableStateFlow(false), ReaderImage.PageId("diagnostic62", 1)
    ) {
        @Volatile var holdFullResize = false
        val fullResizeStarted = CompletableDeferred<Unit>()
        val releaseFullResize = CompletableDeferred<Unit>()
        val previewResizes = AtomicInteger()
        val createdImages = ConcurrentLinkedQueue<Image>()
        val retiredImages = ConcurrentLinkedQueue<Image>()
        override suspend fun resizeImage(image: KomeliaImage, scaleWidth: Int, scaleHeight: Int): ReaderImageData {
            if (scaleWidth <= 768 && scaleHeight <= 768) previewResizes.incrementAndGet()
            if (holdFullResize) { fullResizeStarted.complete(Unit); releaseFullResize.await() }
            return pixels(scaleWidth, scaleHeight)
        }
        override suspend fun getImageRegion(image: KomeliaImage, imageRegion: IntRect, scaleWidth: Int, scaleHeight: Int) = pixels(scaleWidth, scaleHeight)
        private fun pixels(w: Int, h: Int): ReaderImageData {
            val surface = Surface.makeRasterN32Premul(1, 1)
            val bitmap = surface.makeImageSnapshot()
            createdImages.add(bitmap)
            surface.close()
            return ReaderImageData(w, h, listOf(bitmap), null)
        }
        override fun createTilePainter(tiles: List<ReaderImageTile>, displaySize: IntSize, scaleFactor: Double) = ProbePainter(tiles.map { it.displayRegion }, displaySize)
        override fun closeTileBitmaps(tiles: List<ReaderImageTile>) {
            tiles.forEach { tile -> tile.renderImage?.let { retiredImages.add(it); it.close() } }
        }
    }

    private object Decoder : KomeliaImageDecoder {
        override suspend fun decode(encoded: ByteArray, nPages: Int?) = TestImage()
        override suspend fun decodeFromFile(path: String, nPages: Int?) = TestImage()
        override suspend fun decodeAndResize(encoded: ByteArray, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?) = TestImage()
        override suspend fun decodeAndResize(path: String, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?) = TestImage()
    }

    private class TestImage : KomeliaImage {
        override val width = 4000
        override val height = 6000
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
}
