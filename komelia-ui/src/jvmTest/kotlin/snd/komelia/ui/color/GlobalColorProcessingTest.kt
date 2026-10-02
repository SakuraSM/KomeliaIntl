package snd.komelia.ui.color

import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import snd.komelia.color.*
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.image.*
import snd.komelia.image.processing.ColorCorrectionStep
import snd.komga.client.book.KomgaBookId
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class GlobalColorProcessingTest {
    private val book = KomgaBookId("synthetic")
    private fun config(gamma: Float, redGamma: Float = 1f) = DefaultColorCorrection("Gamma", ColorCorrectionConfig(
        ColorCorrectionType.COLOR_LEVELS,
        levels = ColorLevelChannels.DEFAULT.copy(
            color = ColorLevelsConfig.DEFAULT.copy(gamma = gamma),
            red = ColorLevelsConfig.DEFAULT.copy(gamma = redGamma),
        ),
    ))

    private fun repository(): BookColorCorrectionRepository = Proxy.newProxyInstance(
        BookColorCorrectionRepository::class.java.classLoader, arrayOf(BookColorCorrectionRepository::class.java),
    ) { _, method, _ ->
        check(method.name.startsWith("getMode"))
        MutableStateFlow(BookColorCorrectionMode.INHERIT)
    } as BookColorCorrectionRepository

    @Test fun changingTheDefaultInvalidatesVisiblePixelsAndOffRemovesTheCorrection() = runBlocking {
        val global = MutableStateFlow<DefaultColorCorrection?>(null)
        val step = ColorCorrectionStep(repository(), global)
        val changes = AtomicInteger()
        step.addChangeListener { changes.incrementAndGet() }
        step.setBookFlow(MutableStateFlow(book))
        global.value = config(2f)
        withTimeout(5000) { step.channelsLut.first { it?.value != null } }
        withTimeout(5000) { while (changes.get() == 0) delay(1) }
        val first = assertNotNull(step.process(ReaderImage.PageId(book.value, 1), GrayImage())) as GrayImage
        val before = changes.get()
        val previous = step.channelsLut.value
        global.value = config(3f)
        withTimeout(5000) { step.channelsLut.first { it !== previous } }
        withTimeout(5000) { while (changes.get() == before) delay(1) }
        val changed = assertNotNull(step.process(ReaderImage.PageId(book.value, 1), GrayImage())) as GrayImage
        assertNotEquals(first.pixel, changed.pixel)
        global.value = null
        withTimeout(5000) { step.channelsLut.first { it == null } }
        assertNull(step.process(ReaderImage.PageId(book.value, 1), GrayImage()))
    }

    @Test fun rgbPresetOnGrayscaleDoesNotReturnAnAlreadyClosedImage() = runBlocking {
        val step = ColorCorrectionStep(repository(), MutableStateFlow(config(2f, 2f)))
        step.setBookFlow(MutableStateFlow(book))
        withTimeout(5000) { step.channelsLut.first { it?.value != null && it.rgba != null } }
        val result = assertNotNull(step.process(ReaderImage.PageId(book.value, 1), GrayImage())) as GrayImage
        assertFalse(result.closed, "The returned grayscale image is still owned by the caller")
    }

    private class GrayImage(val pixel: Int = 128) : KomeliaImage {
        var closed = false
        override val width = 1
        override val height = 1
        override val bands = 1
        override val type = ImageFormat.GRAYSCALE_8
        override val pagesLoaded = 1
        override val pagesTotal = 1
        override val pageHeight = 1
        override val pageDelays: IntArray? = null
        override suspend fun mapLookupTable(table: ByteArray): KomeliaImage = GrayImage(table[pixel].toInt() and 255)
        override suspend fun getBytes(): ByteArray = byteArrayOf(pixel.toByte())
        override fun close() { closed = true }
        override suspend fun extractArea(rect: ImageRect): KomeliaImage = error("unused")
        override suspend fun resize(scaleWidth: Int, scaleHeight: Int, linear: Boolean, kernel: ReduceKernel): KomeliaImage = error("unused")
        override suspend fun shrink(factor: Double): KomeliaImage = error("unused")
        override suspend fun findTrim(): ImageRect = error("unused")
        override suspend fun makeHistogram(): KomeliaImage = error("unused")
    }
}
