package snd.komelia.ui.reader.image.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.image.*
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider
import snd.komelia.onnxruntime.OnnxRuntimeRfDetr
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.reader.image.PageMetadata
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komga.client.book.*
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Instant

class PanelLoadLifetimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingToAnInFlightPreloadedPageDoesNotCancelOrRestartIt() = fixture { panel, images ->
        panel.onPageChange(1)
        val preloadedImage = awaitImage(images, 3)
        preloadedImage.started.await()
        val preloaded = pendingLoad(panel, panel.pageMetadata.value[2])
        panel.onPageChange(2)
        assertFalse(preloaded.isCancelled, "A page turn must not cancel the destination's in-flight preload")
        preloadedImage.ready.complete(Unit)
        withTimeout(5000) { panel.currentPage.filterNotNull().first { it.metadata.pageNumber == 3 && it.imageResult != null } }
        assertEquals(1, images.count { it.pageId.pageNumber == 3 })
    }

    @Test fun stoppingCancelsPendingLoadsAndClosesTheirAcquiredImages() = fixture { panel, images ->
        panel.onPageChange(1)
        val pendingImage = awaitImage(images, 3)
        pendingImage.started.await()
        val pending = pendingLoad(panel, panel.pageMetadata.value[2])
        panel.stop()
        assertTrue(pending.isCancelled, "Stopping the reader must stop background page work")
        withTimeout(5000) { pending.join() }
        assertEquals(1, pendingImage.closed.get(), "Cancellation after image allocation must release that image")
    }

    private fun fixture(block: suspend (PanelsReaderState, List<ProbeImage>) -> Unit) {
        lateinit var navigator: Navigator
        compose.setContent { Navigator(EmptyScreen) { SideEffect { navigator = it } } }
        compose.waitForIdle()
        val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val images = CopyOnWriteArrayList<ProbeImage>()
        val settings = stub<ImageReaderSettingsRepository> { name, _ -> flowOf(when (name) {
            "getUpsamplingMode" -> UpsamplingMode.BILINEAR
            "getDownsamplingKernel" -> ReduceKernel.MITCHELL
            "getFlashDuration" -> 100L
            "getFlashEveryNPages" -> 1
            "getFlashWith" -> ReaderFlashColor.BLACK
            "getReaderType" -> ReaderType.PANELS
            "getStretchToFit" -> true
            else -> false
        }) }
        val api = stub<KomgaBookApi> { name, _ -> if (name == "getPage") byteArrayOf(1) else error("Unexpected API $name") }
        val reader = ReaderState(
            initialBook = book(), bookApi = api, seriesApi = stub { _, _ -> error("unused") },
            readListApi = stub { _, _ -> error("unused") }, navigator = navigator, appNotifications = AppNotifications(),
            readerSettingsRepository = settings, currentBookId = MutableStateFlow(null), markReadProgress = false,
            stateScope = cleanup, bookSiblingsContext = BookSiblingsContext.Series,
            colorCorrectionRepository = stub { _, _ -> error("unused") }, pageChangeFlow = MutableSharedFlow(),
        )
        val loader = BookImageLoader(MutableStateFlow(api), stub { _, _ -> error("unused") }, object : ReaderImageFactory {
            override suspend fun getImage(imageSource: ImageSource, pageId: ReaderImage.PageId): ReaderImage =
                ProbeImage(pageId).also { images.add(it) }
        }, null)
        val detector = object : KomeliaPanelDetector(stub<OnnxRuntimeRfDetr> { _, _ -> error("Decoder gate should prevent detection") },
            OnnxRuntimeExecutionProvider.CPU, MutableStateFlow(0), emptyFlow()) {
            override fun getModelPath(): String? = null
        }
        val panel = PanelsReaderState(cleanup, settings, AppNotifications(), reader, loader, MutableSharedFlow(), detector, ScreenScaleState())
        panel.screenScaleState.setAreaSize(IntSize(720, 1280))
        panel.pageMetadata.value = (1..3).map { PageMetadata(KomgaBookId("panel-test"), it, IntSize(1988, 3057)) }
        try { runBlocking { block(panel, images) } }
        finally {
            panel.stop()
            // Retire every test-owned scope, even against the old implementation whose stop leaks work.
            PanelsReaderState::class.java.declaredFields.filter { it.type == CoroutineScope::class.java }.forEach {
                it.isAccessible = true
                (it.get(panel) as CoroutineScope).cancel()
            }
            images.forEach { it.ready.complete(Unit) }
            reader.onDispose()
            cleanup.cancel()
        }
    }

    private suspend fun awaitImage(images: List<ProbeImage>, page: Int): ProbeImage = withTimeout(5000) {
        while (images.none { it.pageId.pageNumber == page }) delay(1)
        images.first { it.pageId.pageNumber == page }
    }

    private fun pendingLoad(panel: PanelsReaderState, page: PageMetadata): Deferred<*> {
        val method = PanelsReaderState::class.java.getDeclaredMethod("launchDownload", PageMetadata::class.java)
        method.isAccessible = true
        return method.invoke(panel, page) as Deferred<*>
    }

    private class ProbeImage(override val pageId: ReaderImage.PageId) : ReaderImage {
        val started = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Unit>()
        val closed = AtomicInteger()
        override val originalSize = MutableStateFlow<IntSize?>(IntSize(1988, 3057))
        override val displaySize = MutableStateFlow<IntSize?>(IntSize(720, 1107))
        override val currentSize = MutableStateFlow<IntSize?>(IntSize(720, 1107))
        override val painter = MutableStateFlow<Painter?>(null)
        override val error = MutableStateFlow<Throwable?>(null)
        override fun requestUpdate(maxDisplaySize: IntSize, zoomFactor: Float, visibleDisplaySize: IntRect) = Unit
        override suspend fun getOriginalImageSize() = Result.success(IntSize(1988, 3057))
        override suspend fun getOriginalImage(): Result<KomeliaImage> {
            started.complete(Unit)
            ready.await()
            return Result.failure(IOException("synthetic image without panel data"))
        }
        override fun close() { closed.incrementAndGet() }
    }

    private fun book(): KomeliaBook {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(KomgaBookId("panel-test"), KomgaSeriesId("series"), "Synthetic series", KomgaLibraryId("library"),
            "Synthetic book", "synthetic://book", 1, now, now, now, 20, "20 B",
            Media(KomgaMediaStatus.READY, "application/zip", 3, "", false, false, MediaProfile.DIVINA),
            KomgaBookMetadata("book", "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, now, now),
            null, false, "", false, true, now, false)
    }

    private object EmptyScreen : Screen { @Composable override fun Content() = Unit }
    private inline fun <reified T> stub(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args -> answer(method.name.substringBefore('-'), args.orEmpty()) } as T
}
