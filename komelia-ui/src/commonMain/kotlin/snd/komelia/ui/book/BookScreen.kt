package snd.komelia.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_detail_load_failed
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.error_retry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LoadState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.KomeliaSafeTopBar
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.appRootNavigator
import snd.komelia.ui.readerHostNavigator
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komga.client.book.KomgaBookId
import snd.komga.client.series.KomgaSeriesId
import kotlin.jvm.Transient

fun bookScreen(
    book: KomeliaBook,
    bookSiblingsContext: BookSiblingsContext? = null
): Screen {
    val context = bookSiblingsContext ?: BookSiblingsContext.Series
    return if (book.oneshot) OneshotScreen(book, context)
    else BookScreen(
        book = book,
        bookSiblingsContext = context
    )
}

class BookScreen(
    val bookId: KomgaBookId,
    private val bookSiblingsContext: BookSiblingsContext,
    @Transient
    val book: KomeliaBook? = null,
) : ReloadableScreen {
    constructor(book: KomeliaBook, bookSiblingsContext: BookSiblingsContext) : this(book.id, bookSiblingsContext, book)

    // Reader models must survive disposal of a hidden book-detail navigator.
    override val key: ScreenKey = "book-detail:${bookId.value}"

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(bookId.value) { viewModelFactory.getBookViewModel(bookId, book) }
        val navigator = LocalNavigator.currentOrThrow
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(Unit) {
            vm.initialize()
            reloadEvents.collect { vm.reload() }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventsHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val closePane = LocalBookDetailPaneClose.current
        val book = vm.book.collectAsState().value
        val library = vm.library
        val state = vm.state.collectAsState().value
        val onExit = { closePane?.invoke() ?: onBackPress(navigator, book?.seriesId) }

        LaunchedEffect(book, library) {
            if (book?.oneshot == true && library?.id == book.libraryId) {
                navigator.replace(OneshotScreen(book, bookSiblingsContext))
            }
        }

        BackPressHandler(onExit)

        ScreenPullToRefreshBox(
            screenState = vm.state,
            onRefresh = vm::reload
        ) {
            if (book != null && library?.id == book.libraryId) {
                BookScreenContent(
                    library = library,
                    book = book,
                    bookMenuActions = vm.bookMenuActions,
                    onBookReadPress = { markReadProgress ->
                        navigator.readerHostNavigator().push(
                            readerScreen(
                                book = book,
                                bookSiblingsContext = bookSiblingsContext,
                                markReadProgress = markReadProgress
                            )
                        )
                    },
                    onBookDownload = vm::onBookDownload,
                    onBookDownloadDelete = vm::onBookDownloadDelete,

                    readLists = vm.readListsState.readLists,
                    onReadListClick = { navigator.push(ReadListScreen(it.id)) },
                    onReadListBookPress = { book, readList ->
                        if (book.id != bookId) navigator.push(
                            bookScreen(
                                book = book,
                                bookSiblingsContext = BookSiblingsContext.ReadList(readList.id)
                            )
                        )
                    },
                    onParentSeriesPress = { if (closePane != null) closePane()
                        else navigator.push(SeriesScreen(book.seriesId)) },
                    onFilterClick = { filter ->
                        navigator.push(LibraryScreen(book.libraryId, filter))
                    },
                    onBackPress = onExit,
                    cardWidth = vm.cardWidth.collectAsState().value,
                    readingContext = bookSiblingsContext,
                )
            } else if (state is LoadState.Error) {
                ErrorContent(
                    message = stringResource(Res.string.book_detail_load_failed),
                    onReload = vm::reload,
                    onExit = onExit,
                    exitLabel = stringResource(Res.string.navigation_back),
                    reloadLabel = stringResource(Res.string.error_retry),
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    KomeliaSafeTopBar {
                        IconButton(onClick = onExit) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
                        }
                    }
                    LoadingMaxSizeIndicator(Modifier.weight(1f))
                }
            }
        }
    }

    private fun onBackPress(navigator: Navigator, seriesId: KomgaSeriesId?) {
        if (navigator.canPop) {
            navigator.pop()
        } else {
            when (val context = bookSiblingsContext) {
                is BookSiblingsContext.ReadList -> navigator.replace(ReadListScreen(context.id))
                BookSiblingsContext.Series -> if (seriesId != null) {
                    navigator.replace(SeriesScreen(seriesId))
                } else {
                    navigator.appRootNavigator().replaceAll(MainScreen())
                }
            }

        }
    }

}
