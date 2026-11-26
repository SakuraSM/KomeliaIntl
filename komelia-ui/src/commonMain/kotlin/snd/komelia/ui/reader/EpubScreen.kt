package snd.komelia.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.reader.epub.EpubChromeAppearance
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.epub_open_failed
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.error_retry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.LocalWindowState
import snd.komelia.ui.LocalMacOS
import snd.komelia.ui.MainScreen
import snd.komelia.ui.newReaderSessionId
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komelia.ui.platform.canIntegrateWithSystemBar
import snd.komelia.ui.reader.epub.EpubContent
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.MediaProfile
import kotlin.jvm.Transient

class EpubScreen(
    private val bookId: KomgaBookId,
    private val bookSiblingsContext: BookSiblingsContext,
    private val markReadProgress: Boolean = true,
    @Transient
    private val book: KomeliaBook? = null,
    private var initialChapterHref: String? = null,
) : Screen {

    private var sessionId: String? = newReaderSessionId()
    override val key: ScreenKey
        get() = "epub-reader:${bookId.value}:${if (markReadProgress) "tracked" else "incognito"}:${sessionId ?: newReaderSessionId().also { sessionId = it }}"

    private companion object {
        private const val serialVersionUID = 2990348300915720399L
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(bookId.value) {
            viewModelFactory.getEpubReaderViewModel(
                bookId = bookId,
                bookSiblingsContext = bookSiblingsContext,
                book = book,
                markReadProgress = markReadProgress,
            )
        }
        val appearance = LocalSurfaceAppearance.current
        val reducedMotion = LocalKomeliaMotion.current.isReducedMotion
        val mac = LocalMacOS.current
        val android = snd.komelia.ui.LocalPlatform.current == snd.komelia.ui.platform.PlatformType.MOBILE
        SideEffect {
            vm.chromeAppearance.value = EpubChromeAppearance(
                surfaceStyle = appearance.style,
                theme = appearance.theme,
                reduceTransparency = appearance.reduceTransparency,
                reducedMotion = reducedMotion,
                renderingSupported = appearance.renderingSupported,
                isMacOS = mac,
                isAndroid = android,
            )
        }
        LaunchedEffect(bookId) {
            vm.initialize(navigator, initialChapterHref)
            initialChapterHref = null
            val state = vm.state.value
            if (state is LoadState.Success) {
                val book = state.value.book.value
                if (book != null && book.media.mediaProfile != MediaProfile.EPUB) {
                    navigator.replace(readerScreen(book, markReadProgress, bookSiblingsContext))
                }
            }
        }

        val state = vm.state.collectAsState().value
        val reader = (state as? LoadState.Success)?.value
        val readerState = reader?.state?.collectAsState()?.value
        val isError = state is LoadState.Error || readerState is LoadState.Error
        val scope = rememberCoroutineScope()
        val onExit: () -> Unit = {
            if (navigator.canPop) navigator.pop()
            else {
                val screen = (reader?.book?.value ?: book)?.let {
                    bookScreen(book = it, bookSiblingsContext = bookSiblingsContext)
                } ?: BookScreen(bookId = bookId, bookSiblingsContext = bookSiblingsContext)
                navigator.replaceAll(MainScreen(screen))
            }
        }
        Column {
            PlatformTitleBar(applyInsets = false) {
                if (LocalMacOS.current) {
                    val isFullscreen = LocalWindowState.current.isFullscreen.collectAsState(false)
                    if (!isFullscreen.value) {
                        val title = if (state is LoadState.Success) state.value.book.collectAsState().value?.metadata?.title else null
                        MacReaderTitleBar(title, if (isError || reader == null) onExit else reader::onBackButtonPress)
                    }
                } else if (canIntegrateWithSystemBar()) {
                    val isFullscreen = LocalWindowState.current.isFullscreen.collectAsState(false)
                    if (state is LoadState.Success && !isFullscreen.value) {
                        val book = state.value.book.collectAsState().value
                        TitleBarContent(
                            title = book?.metadata?.title ?: "",
                            onExit = if (isError) onExit else state.value::onBackButtonPress
                        )
                    }
                }
            }
            when {
                isError -> {
                    ErrorContent(
                        message = stringResource(Res.string.epub_open_failed),
                        onReload = { scope.launch { vm.retry(navigator) } },
                        onExit = onExit,
                        reloadLabel = stringResource(Res.string.error_retry),
                        exitLabel = stringResource(Res.string.navigation_back),
                    )
                    BackPressHandler(onExit)
                }
                reader == null -> {
                    LoadingMaxSizeIndicator()
                    BackPressHandler(onExit)
                }
                else -> key(reader) {
                    EpubContent(
                        onWebviewCreated = reader::onWebviewCreated,
                        onBackButtonPress = reader::onBackButtonPress,
                        contentReady = reader.contentReady.collectAsState().value,
                        displaySettings = vm.displaySettings.collectAsState().value,
                        backgroundColor = reader.backgroundColor.collectAsState().value,
                    )
                }
            }
        }
    }

}
