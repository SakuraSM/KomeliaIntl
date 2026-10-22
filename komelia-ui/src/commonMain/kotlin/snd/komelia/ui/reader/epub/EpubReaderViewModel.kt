package snd.komelia.ui.reader.epub

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CancellationException
import snd.komelia.settings.model.EpubReaderType
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import snd.komelia.settings.model.EpubDisplaySettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import snd.komelia.AppNotifications
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
    private val notifications: AppNotifications,
    private val windowState: AppWindowState,
    private val platformType: PlatformType,
    private val bookSiblingsContext: BookSiblingsContext,
) : StateScreenModel<LoadState<EpubReaderState>>(LoadState.Uninitialized) {

    val displaySettings = MutableStateFlow(EpubDisplaySettings())
    val chromeAppearance = MutableStateFlow(EpubChromeAppearance())

    private val retainedReader = RetainedEpubReader()

    override fun onDispose() {
        retainedReader.dispose()
        super.onDispose()
    }

    suspend fun initialize(navigator: Navigator) {
        if (state.value is LoadState.Loading) return
        displaySettings.value = epubSettingsRepository.getDisplaySettings().first()
        val selectedType = epubSettingsRepository.getReaderType().first()
        val reader = retainedReader.select(selectedType) { createReader(selectedType) }
        if ((state.value as? LoadState.Success)?.value !== reader) mutableState.value = LoadState.Loading
        try {
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
            mutableState.value = LoadState.Error(error)
        }
    }

    private suspend fun createReader(type: EpubReaderType): EpubReaderState = when (type) {
        KOMGA_EPUB -> KomgaEpubReaderState(
            bookId = bookId,
            book = book,
            bookApi = bookApi,
            seriesApi = seriesApi,
            readListApi = readListApi,
            serverUrl = serverUrl,
            notifications = notifications,
            markReadProgress = markReadProgress,
            epubSettingsRepository = epubSettingsRepository,
            displaySettings = displaySettings,
            chromeAppearance = chromeAppearance,
            windowState = windowState,
            platformType = platformType,
            coroutineScope = screenModelScope,
            bookSiblingsContext = bookSiblingsContext,
        )
        TTSU_EPUB -> TtsuReaderState(
            bookId = bookId,
            book = book,
            bookApi = bookApi,
            notifications = notifications,
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
        )
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
