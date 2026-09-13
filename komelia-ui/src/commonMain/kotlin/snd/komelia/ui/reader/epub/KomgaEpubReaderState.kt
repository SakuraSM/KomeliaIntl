package snd.komelia.ui.reader.epub

import androidx.compose.ui.graphics.Color
import snd.komelia.settings.model.EpubDisplaySettings
import cafe.adriel.voyager.navigator.Navigator
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser.Companion.xmlParser
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.ExperimentalResourceApi
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.MainScreen
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.R2Progression
import snd.komga.client.readlist.KomgaReadListId
import snd.komga.client.series.KomgaSeriesId
import snd.webview.KomeliaWebview
import snd.webview.ResourceLoadResult

private val logger = KotlinLogging.logger {}
class KomgaEpubReaderState(
    bookId: KomgaBookId,
    book: KomeliaBook?,
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val readListApi: KomgaReadListApi,
    private val serverUrl: StateFlow<String>,
    private val epubSettingsRepository: EpubReaderSettingsRepository,
    private val displaySettings: StateFlow<EpubDisplaySettings>,
    private val notifications: AppNotifications,
    private val markReadProgress: Boolean,
    private val windowState: AppWindowState,
    private val platformType: PlatformType,
    private val coroutineScope: CoroutineScope,
    private val bookSiblingsContext: BookSiblingsContext,
) : EpubReaderState {
    override val state = MutableStateFlow<LoadState<Unit>>(Uninitialized)
    override val book = MutableStateFlow(book)
    override val backgroundColor = MutableStateFlow(Color.White)
    override val contentReady = MutableStateFlow(false)

    val bookId = MutableStateFlow(bookId)
    private val webview = MutableStateFlow<KomeliaWebview?>(null)
    private val navigator = MutableStateFlow<Navigator?>(null)
    private val isClosed = MutableStateFlow(false)
    private var activeSession: EpubReaderSession? = null

    override suspend fun initialize(navigator: Navigator) {
        isClosed.value = false
        this.navigator.value = navigator
        if (platformType == PlatformType.MOBILE) windowState.setFullscreen(true, hideNavigationBar = displaySettings.value.immersiveMode)
        if (state.value !is Uninitialized) return

        state.value = LoadState.Loading
        notifications.runCatchingToNotifications {
            Res.getUri("files/komga.html")
            if (book.value == null) book.value = bookApi.getOne(bookId.value)
            state.value = LoadState.Success(Unit)
        }.onFailure {
            state.value = LoadState.Error(it)
        }
    }

    override fun onWebviewCreated(webview: KomeliaWebview) {
        // A retained screen can create its native view before initialize() runs again.
        isClosed.value = false
        activeSession?.close()
        val session = EpubReaderSession().also { activeSession = it }
        contentReady.value = false
        this.webview.value = webview
        coroutineScope.launch { loadEpub(webview, session) }
    }

    override fun onBackButtonPress() {
        closeWebview()
    }

    override fun closeWebview() {
        if (!isClosed.compareAndSet(false, true)) return
        retireWebview()
        if (platformType == PlatformType.MOBILE) windowState.setFullscreen(false)
        navigator.value?.let { nav ->
            if (nav.canPop) nav.pop()
            else {
                val screen = book.value?.let { bookScreen(book = it, bookSiblingsContext = bookSiblingsContext) }
                    ?: BookScreen(bookId = bookId.value, bookSiblingsContext = bookSiblingsContext)
                nav.replaceAll(MainScreen(screen))
            }
        }
    }

    override fun dispose() {
        if (!isClosed.compareAndSet(false, true)) return
        retireWebview()
        if (platformType == PlatformType.MOBILE) windowState.setFullscreen(false)
    }

    private fun retireWebview() {
        activeSession?.close()
        activeSession = null
        val closing = webview.value
        webview.value = null
        contentReady.value = false
        // Android close() does not destroy the document. Retire its JS realm explicitly.
        closing?.navigate("about:blank")
        closing?.close()
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadEpub(webview: KomeliaWebview, session: EpubReaderSession) {
        session.requireOpen()
        bindActive<Unit, String>(webview, session, "bookId") {
            bookId.value.value
        }
        bindActive<Unit, Boolean>(webview, session, "incognito") {
            !markReadProgress
        }
        bindActive<KomgaBookId, KomeliaBook>(webview, session, "bookGet") { bookId: KomgaBookId ->
            val book = bookApi.getOne(bookId)
            session.requireOpen()
            this.book.value = book
            this.bookId.value = book.id
            book
        }
        bindActive(webview, session, "bookGetProgression") { bookId: KomgaBookId ->
            bookApi.getReadiumProgression(bookId)
                ?.let { progressionToWebview(it) }
        }

        @Serializable
        data class BookUpdateProgression(val bookId: KomgaBookId, val progression: R2Progression)
        webview.bind("bookUpdateProgression") { request: BookUpdateProgression ->
            // Preserve a queued final progress write for the book being closed.
            bookApi.updateReadiumProgression(request.bookId, progressionFromWebview(request.progression))
        }

        bindActive(webview, session, "bookGetBookSiblingNext") { bookId: KomgaBookId ->
            bookApi.getBookSiblingNext(bookId)
        }

        bindActive(webview, session, "bookGetBookSiblingPrevious") { bookId: KomgaBookId ->
            bookApi.getBookSiblingPrevious(bookId)
        }

        bindActive(webview, session, "getOneSeries") { seriesId: KomgaSeriesId ->
            seriesApi.getOneSeries(seriesId)
        }

        bindActive(webview, session, "readListGetOne") { readListId: KomgaReadListId ->
            readListApi.getOne(readListId)
        }

        bindActive(webview, session, "d2ReaderGetContent") { href: String ->
            getD2Content(href)
        }
        bindActive(webview, session, "d2ReaderGetContentBytesLength") { href: String ->
            proxyResourceRequest(bookApi, href, serverUrl).data.size
        }

        bindActive(webview, session, "externalFetch") { href: String ->
            proxyResourceRequest(bookApi, href, serverUrl).data.decodeToString()
        }

        bindActive(webview, session, "getPublication") { bookId: KomgaBookId ->
            bookApi.getWebPubManifest(bookId)
        }

        webview.bind<Unit, Unit>("closeBook") { session.requireOpen(); closeWebview() }

        bindActive<Unit, String>(webview, session, "getServerUrl") {
            serverUrl.first()
        }

        bindActive<Unit, JsonObject>(webview, session, "getSettings") {
            epubSettingsRepository.getKomgaReaderSettings().also {
                session.requireOpen()
                backgroundColor.value = komgaReaderBackground(it)
            }
        }

        bindActive<JsonObject, Unit>(webview, session, "saveSettings") { newSettings ->
            backgroundColor.value = komgaReaderBackground(newSettings)
            epubSettingsRepository.putKomgaReaderSettings(newSettings)
        }
        bindActive<Unit, Boolean>(webview, session, "isFullscreenAvailable") {
            platformType != PlatformType.MOBILE
        }
        bindActive<Unit, Unit>(webview, session, "toggleFullscreen") {
            val fullscreen = windowState.isFullscreen.first()
            session.requireOpen()
            windowState.setFullscreen(!fullscreen, hideNavigationBar = displaySettings.value.immersiveMode)
        }
        bindActive<Unit, Unit>(webview, session, "readerContentReady") {
            contentReady.value = true
        }

        webview.registerRequestInterceptor { request ->
            runCatching {
                session.run {
                    when (val urlString = request.url.toString()) {
                        "http://komelia/komga.html" -> {
                            val bytes = Res.readBytes("files/komga.html")
                            ResourceLoadResult(data = bytes, contentType = "text/html")
                        }

                        "http://komelia/favicon.ico" -> null
                        else -> proxyResourceRequest(bookApi, urlString, serverUrl)
                    }
                }
            }.onFailure { if (it !is CancellationException) logger.catching(it) }.getOrNull()
        }

        session.requireOpen()
        webview.navigate("http://komelia/komga.html")
        webview.start()
    }

    private suspend inline fun <reified Args, reified Result> bindActive(
        webview: KomeliaWebview,
        session: EpubReaderSession,
        name: String,
        crossinline request: suspend (Args) -> Result,
    ) {
        webview.bind<Args, Result>(name) { arguments -> session.run { request(arguments) } }
    }

    private suspend fun progressionToWebview(progress: R2Progression): R2Progression {
        val baseUrl = serverUrl.first()
        val resourceName = epubResourceName(progress.locator.href)
        val href = if (book.value?.libraryId?.isLocalLibrary() == true) {
            bookResourceUrl(bookId.value, resourceName)
        } else {
            "$baseUrl/api/v1/books/${bookId.value}/resource/$resourceName"
        }
        return progress.copy(
            locator = progress.locator.copy(
                href = href,
            )
        )
    }

    private fun progressionFromWebview(progress: R2Progression): R2Progression {
        return progress.copy(
            locator = progress.locator.copy(
                href = epubResourceName(progress.locator.href),
            )
        )
    }

    private suspend fun getD2Content(url: String): String? {
        return runCatching {
            val textResponse = proxyResourceRequest(
                bookApi = bookApi,
                urlString = url,
                serverUrl = serverUrl
            ).data.decodeToString()
            if (platformType == WEB_KOMF) {
                val document = Ksoup.parse(textResponse, xmlParser()) //strict xhtml rules
                addCrossOriginToElements(document)
                document.outerHtml()
            } else textResponse
        }
            .onFailure { logger.catching(it) }
            .getOrNull()
    }

    private fun addCrossOriginToElements(body: Element) {
        buildList {
            addAll(body.getElementsByTag("link"))
            addAll(body.getElementsByTag("img"))
            addAll(body.getElementsByTag("image"))
        }.forEach { it.attr("crossorigin", "use-credentials") }
    }
}
