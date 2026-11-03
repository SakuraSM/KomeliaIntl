package snd.komelia.ui.series

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.ReadingDesign
import snd.komelia.ui.KomeliaDesktopBrowseTypography
import androidx.compose.material3.MaterialTheme
import snd.komelia.ui.search.SearchScreen
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.DestinationContent
import snd.komelia.ui.readerHostNavigator
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.LocalBookDetailPaneClose
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.book.KomgaBookId
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen
import snd.komelia.ui.series.SeriesViewModel.SeriesTab
import snd.komelia.ui.series.view.SeriesContent
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.jvm.Transient

fun seriesScreen(series: KomeliaSeries): Screen =
    if (series.oneshot) OneshotScreen(series, BookSiblingsContext.Series)
    else SeriesScreen(series)

class SeriesScreen(
    val seriesId: KomgaSeriesId,
    @Transient
    private val series: KomeliaSeries? = null,
    @Transient
    private val startingTab: SeriesTab? = SeriesTab.BOOKS,
) : ReloadableScreen {

    constructor(series: KomeliaSeries, startingTab: SeriesTab = SeriesTab.BOOKS) : this(
        series.id,
        series,
        startingTab
    )

    override val key: ScreenKey = seriesId.toString()

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(seriesId.value) {
            viewModelFactory.getSeriesViewModel(seriesId, series, startingTab)
        }
        val reloadEvents = LocalReloadEvents.current
        LaunchedEffect(seriesId) {
            vm.initialize()
            val series = vm.series.value
            if (series != null && series.oneshot) {
                navigator.replace(OneshotScreen(series, BookSiblingsContext.Series))
                return@LaunchedEffect
            }
            reloadEvents.collect { vm.reload() }
        }

        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        val scrollState = rememberLazyGridState()
        var selectedBookId by rememberSaveable(seriesId.value) { mutableStateOf<String?>(null) }
        var paneClosed by rememberSaveable(seriesId.value) { mutableStateOf(false) }
        val targetState = vm.booksState.readingTarget.collectAsState().value
        val target = (targetState as? snd.komelia.ui.LoadState.Success)?.value
        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(
                    exception = state.exception,
                    onReload = vm::reload
                )

                else -> {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val canShowPane = maxWidth >= 680.dp
                        val full = LocalWindowWidth.current == WindowSizeClass.FULL
                        val listWidth = if (full) ReadingDesign.desktopMasterWidth else ReadingDesign.tabletMasterWidth
                        val platform = LocalPlatform.current
                        val inheritedWidth = LocalWindowWidth.current
                        LaunchedEffect(target?.id, paneClosed, canShowPane) {
                            if (canShowPane && !paneClosed && selectedBookId == null) selectedBookId = target?.id?.value
                        }
                        val listContent: @Composable () -> Unit = {
                    SeriesContent(
                        series = vm.series.collectAsState().value,
                        mangaBaka = vm.mangaBakaState.collectAsState().value,
                        library = vm.library.collectAsState().value,
                        onLibraryClick = { navigator.push(LibraryScreen(it.id)) },
                        seriesMenuActions = vm.seriesMenuActions(),
                        onFilterClick = { filter ->
                            val series = requireNotNull(vm.series.value)
                            navigator.push(LibraryScreen(series.libraryId, filter))
                        },

                        currentTab = vm.currentTab,
                        onTabChange = vm::onTabChange,

                        booksState = vm.booksState,
                        onBookClick = {
                            if (canShowPane) { selectedBookId = it.id.value; paneClosed = false }
                            else navigator push bookScreen(it)
                        },
                        onBookReadClick = { book, markProgress ->
                            navigator.readerHostNavigator().push(readerScreen(book, markProgress))
                        },

                        collectionsState = vm.collectionsState,
                        onCollectionClick = { collection -> navigator.push(CollectionScreen(collection.id)) },
                        onSeriesClick = { series ->
                            navigator.push(
                                if (series.oneshot) OneshotScreen(series, BookSiblingsContext.Series)
                                else SeriesScreen(series, vm.currentTab)
                            )
                        },
                        onDownload = vm::onDownload,
                        scrollState = scrollState,
                        isMasterPane = canShowPane && selectedBookId != null,
                        masterPaneGrid = canShowPane && full,
                        activeBookId = selectedBookId?.let(::KomgaBookId),
                        onSearchAll = { navigator.push(SearchScreen(it)) },
                        onBackPress = { onBackPress(navigator, vm.series.value?.libraryId) },
                    )
                        }
                        val selected = selectedBookId
                        if (selected != null) Row(Modifier.fillMaxSize()) {
                            if (canShowPane) {
                            CompositionLocalProvider(LocalWindowWidth provides WindowSizeClass.COMPACT,
                                LocalKomeliaLayout provides komeliaLayoutSpec(platform, WindowSizeClass.COMPACT)) {
                                Box(Modifier.width(listWidth).fillMaxHeight()) {
                                    if (full) MaterialTheme(typography = KomeliaDesktopBrowseTypography) { listContent() } else listContent()
                                }
                            }
                            VerticalDivider()
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                CompositionLocalProvider(
                                    LocalWindowWidth provides if (canShowPane && inheritedWidth != WindowSizeClass.FULL) WindowSizeClass.EXPANDED else inheritedWidth,
                                    LocalKomeliaLayout provides komeliaLayoutSpec(platform, if (canShowPane) WindowSizeClass.EXPANDED else inheritedWidth),
                                    LocalBookDetailPaneClose provides { selectedBookId = null; paneClosed = true },
                                ) {
                                    key(selected) {
                                        Navigator(BookScreen(KomgaBookId(selected), BookSiblingsContext.Series),
                                            key = "series-detail:${seriesId.value}:$selected") { DestinationContent(it) }
                                    }
                                }
                            }
                        } else listContent()
                    }
                }
            }

            BackPressHandler {
                vm.series.value?.let { onBackPress(navigator, it.libraryId) }
            }
        }
    }

    private fun onBackPress(navigator: Navigator, libraryId: KomgaLibraryId?) {
        if (navigator.canPop) {
            navigator.pop()
        } else {
            libraryId?.let { navigator replaceAll LibraryScreen(it) }
        }
    }
}
