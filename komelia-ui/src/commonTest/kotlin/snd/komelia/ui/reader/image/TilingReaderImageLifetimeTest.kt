package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import snd.komelia.image.ImageFormat
import snd.komelia.image.ImageRect
import snd.komelia.image.ImageSource
import snd.komelia.image.KomeliaImage
import snd.komelia.image.KomeliaImageDecoder
import snd.komelia.image.ReaderImage
import snd.komelia.image.ReduceKernel
import snd.komelia.image.TilingReaderImage
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.processing.ImageProcessingPipeline
import snd.komelia.image.processing.ProcessingStep
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TilingReaderImageLifetimeTest {
    @Test
    fun pipelineChangeDoesNotCloseAnImageUsedBySuspendedResize() = runTest {
        val fixture = fixture()
        try {
            fixture.step.changed()
            runCurrent()
            assertFalse(fixture.inUse.closed.value, "Crop reload must wait for the in-flight native resize")
        } finally {
            fixture.reader.close()
            fixture.reader.releaseResize.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun closeWaitsForInFlightNativeOperationBeforeReleasingImages() = runTest {
        val fixture = fixture()
        try {
            fixture.reader.close()
            runCurrent()
            assertFalse(fixture.inUse.closed.value, "Cancelling a coroutine does not finish its native operation")
        } finally {
            fixture.reader.close()
            fixture.reader.releaseResize.complete(Unit)
            runCurrent()
        }
        assertTrue(fixture.inUse.closed.value)
    }

    @Test
    fun unprocessedImageIsReleasedOnlyOnceOnRepeatedClose() = runTest {
        val reader = HoldingReader(ImageProcessingPipeline(), StandardTestDispatcher(testScheduler))
        runCurrent()
        val original = reader.getOriginalImage().getOrThrow() as TrackedImage
        reader.close()
        reader.close()
        runCurrent()
        assertEquals(1, original.closeCount)
    }

    private suspend fun TestScope.fixture(): Fixture {
        val pipeline = ImageProcessingPipeline()
        val step = ReplacementStep()
        // Drain addStep's initial notification before constructing the reader.
        val registered = async(start = CoroutineStart.UNDISPATCHED) { pipeline.changeFlow.first() }
        pipeline.addStep(step)
        registered.await()
        val reader = HoldingReader(pipeline, StandardTestDispatcher(testScheduler))
        runCurrent()
        reader.requestUpdate(IntSize(100, 100), 1f, IntRect(0, 0, 100, 100))
        return Fixture(reader, step, reader.resizeStarted.await())
    }

    private class Fixture(val reader: HoldingReader, val step: ReplacementStep, val inUse: TrackedImage)

    private class ReplacementStep : ProcessingStep {
        lateinit var changed: () -> Unit
        override suspend fun process(pageId: ReaderImage.PageId, image: KomeliaImage) = TrackedImage()
        override suspend fun addChangeListener(callback: () -> Unit) { changed = callback }
    }

    private class HoldingReader(pipeline: ImageProcessingPipeline, dispatcher: CoroutineDispatcher) : TilingReaderImage(
        imageSource = ImageSource.MemorySource(byteArrayOf()),
        imageDecoder = TestDecoder,
        processingPipeline = pipeline,
        stretchImages = MutableStateFlow(false),
        upsamplingMode = MutableStateFlow(UpsamplingMode.NEAREST),
        downSamplingKernel = MutableStateFlow(ReduceKernel.NEAREST),
        linearLightDownSampling = MutableStateFlow(false),
        pageId = ReaderImage.PageId("native-lifetime-test", 1),
        processingDispatcher = dispatcher,
    ) {
        val resizeStarted = CompletableDeferred<TrackedImage>()
        val releaseResize = CompletableDeferred<Unit>()
        override suspend fun resizeImage(image: KomeliaImage, scaleWidth: Int, scaleHeight: Int): ReaderImageData {
            resizeStarted.complete(image as TrackedImage)
            // A JNI operation can finish after coroutine cancellation was requested.
            withContext(NonCancellable) { releaseResize.await() }
            throw CancellationException("Test resize finished")
        }
        override suspend fun getImageRegion(image: KomeliaImage, imageRegion: IntRect, scaleWidth: Int, scaleHeight: Int): ReaderImageData = error("Not a tiled request")
        override fun createTilePainter(tiles: List<ReaderImageTile>, displaySize: IntSize, scaleFactor: Double): TiledPainter = error("Resize intentionally suspended")
        override fun closeTileBitmaps(tiles: List<ReaderImageTile>) = Unit
    }

    private object TestDecoder : KomeliaImageDecoder {
        override suspend fun decode(encoded: ByteArray, nPages: Int?) = TrackedImage()
        override suspend fun decodeFromFile(path: String, nPages: Int?) = TrackedImage()
        override suspend fun decodeAndResize(encoded: ByteArray, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?) = TrackedImage()
        override suspend fun decodeAndResize(path: String, scaleWidth: Int, scaleHeight: Int, crop: Boolean, nPages: Int?) = TrackedImage()
    }

    private class TrackedImage : KomeliaImage {
        val closed = MutableStateFlow(false)
        var closeCount = 0
        override val width = 100
        override val height = 100
        override val bands = 4
        override val type = ImageFormat.RGBA_8888
        override val pagesLoaded = 1
        override val pagesTotal = 1
        override val pageHeight = 100
        override val pageDelays: IntArray? = null
        override suspend fun extractArea(rect: ImageRect): KomeliaImage = error("Unexpected native call")
        override suspend fun resize(scaleWidth: Int, scaleHeight: Int, linear: Boolean, kernel: ReduceKernel): KomeliaImage = error("Unexpected native call")
        override suspend fun shrink(factor: Double): KomeliaImage = error("Unexpected native call")
        override suspend fun findTrim(): ImageRect = error("Unexpected native call")
        override suspend fun makeHistogram(): KomeliaImage = error("Unexpected native call")
        override suspend fun mapLookupTable(table: ByteArray): KomeliaImage = error("Unexpected native call")
        override suspend fun getBytes(): ByteArray = error("Unexpected native call")
        override fun close() {
            closeCount++
            closed.value = true
        }
    }
}
