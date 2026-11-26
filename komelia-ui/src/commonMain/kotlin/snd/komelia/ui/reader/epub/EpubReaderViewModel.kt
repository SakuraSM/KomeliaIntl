package snd.komelia.ui.reader.epub

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CancellationException
import snd.komelia.settings.model.EpubReaderType
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import io.github.oshai.kotlinlogging.KotlinLogging
import snd.komelia.settings.model.EpubDisplaySettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import snd.komelia.AppWindowState
import snd.komelia.fonts.UserFontsRepository
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.EpubReaderSettingsRepository
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.EpubReaderType.KOMGA_EPUB
import snd.komelia.settings.model.EpubReaderType.TTSU_EPUB
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.explicitLocaleTag
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.book.KomgaBookId
import snd.webview.KomeliaWebview

private val logger = KotlinLogging.logger {}

class EpubReaderViewModel(
    private val bookId: KomgaBookId,
    private val book: KomeliaBook?,
    private val markReadProgress: Boolean,
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val readListApi: KomgaReadListApi,
    private val serverUrl: StateFlow<String>,
    private val epubSettingsRepository: EpubReaderSettingsRepository,
    private val settingsRepository: CommonSettingsRepository,
    private val fontsRepository: UserFontsRepository,
    private val windowState: AppWindowState,
    private val platformType: PlatformType,
    private val bookSiblingsContext: BookSiblingsContext,
) : StateScreenModel<LoadState<EpubReaderState>>(LoadState.Uninitialized) {

    val displaySettings = MutableStateFlow(EpubDisplaySettings())
    val chromeAppearance = MutableStateFlow(EpubChromeAppearance())

    private val retainedReader = RetainedEpubReader()
    private var openingBookId = bookId
    private var openingChapter: String? = null

    override fun onDispose() {
        retainedReader.dispose()
        super.onDispose()
    }

    suspend fun initialize(navigator: Navigator, initialChapterHref: String? = null) {
        if (state.value is LoadState.Loading) return
        openReader(navigator, bookId, initialChapterHref, replaceExisting = initialChapterHref != null)
    }

    suspend fun retry(navigator: Navigator) {
        if (state.value is LoadState.Loading) return
        val previous = (state.value as? LoadState.Success)?.value as? KomgaEpubReaderState
        val retryBookId = previous?.bookId?.value ?: openingBookId
        val retryChapter = previous?.failedChapter ?: openingChapter.takeIf { retryBookId == openingBookId }
        openReader(navigator, retryBookId, retryChapter, replaceExisting = true)
    }

    private suspend fun openReader(
        navigator: Navigator,
        targetBookId: KomgaBookId,
        chapterHref: String?,
        replaceExisting: Boolean,
    ) {
        if (replaceExisting || state.value !is LoadState.Success) {
            openingBookId = targetBookId
            openingChapter = chapterHref
            mutableState.value = LoadState.Loading
        }
        try {
            displaySettings.value = epubSettingsRepository.getDisplaySettings().first()
            val selectedType = epubSettingsRepository.getReaderType().first()
            val reader = retainedReader.select(selectedType, replaceExisting) {
                createReader(selectedType, targetBookId, chapterHref)
            }
            reader.initialize(navigator)
            mutableState.value = when (val result = reader.state.value) {
                is LoadState.Error -> LoadState.Error(result.exception)
                is LoadState.Success -> LoadState.Success(reader)
                LoadState.Loading, LoadState.Uninitialized -> error("EPUB reader did not initialize")
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = LoadState.Uninitialized
            throw cancelled
        } catch (error: Exception) {
            logger.catching(error)
            mutableState.value = LoadState.Error(error)
        }
    }

    private suspend fun createReader(type: EpubReaderType, targetBookId: KomgaBookId, initialChapterHref: String?): EpubReaderState {
        var pendingChapter = initialChapterHref
        val consumeInitialChapter = { pendingChapter.also { pendingChapter = null } }
        return when (type) {
        KOMGA_EPUB -> KomgaEpubReaderState(
            bookId = targetBookId,
            book = book?.takeIf { it.id == targetBookId },
            bookApi = bookApi,
            seriesApi = seriesApi,
            readListApi = readListApi,
            serverUrl = serverUrl,
            markReadProgress = markReadProgress,
            epubSettingsRepository = epubSettingsRepository,
            displaySettings = displaySettings,
            chromeAppearance = chromeAppearance,
            windowState = windowState,
            platformType = platformType,
            coroutineScope = screenModelScope,
            bookSiblingsContext = bookSiblingsContext,
            consumeInitialChapter = consumeInitialChapter,
        )
        TTSU_EPUB -> TtsuReaderState(
            bookId = targetBookId,
            book = book?.takeIf { it.id == targetBookId },
            bookApi = bookApi,
            markReadProgress = markReadProgress,
            serverUrl = serverUrl,
            epubSettingsRepository = epubSettingsRepository,
            displaySettings = displaySettings,
            chromeAppearance = chromeAppearance,
            localeTag = settingsRepository.getAppLanguage().first().explicitLocaleTag(),
            fontsRepository = fontsRepository,
            windowState = windowState,
            platformType = platformType,
            coroutineScope = screenModelScope,
            bookSiblingsContext = bookSiblingsContext,
            consumeInitialChapter = consumeInitialChapter,
        )
    }
    }

}

interface EpubReaderState {
    val state: StateFlow<LoadState<Unit>>
    val book: StateFlow<KomeliaBook?>
    val contentReady: StateFlow<Boolean>
    val backgroundColor: StateFlow<Color>
    suspend fun initialize(navigator: Navigator)
    fun onWebviewCreated(webview: KomeliaWebview)
    fun onBackButtonPress()
    fun closeWebview()
    fun dispose() {}
}
