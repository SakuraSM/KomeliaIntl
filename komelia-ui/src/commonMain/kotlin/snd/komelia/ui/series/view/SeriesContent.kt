package snd.komelia.ui.series.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.ExpandMore
import snd.komelia.ui.common.components.KomeliaDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import snd.komelia.ui.common.components.KomeliaPlainTooltip as PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_series_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_file_source
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_books_count
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_sibling_retry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_actions
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_identify
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_link_series
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_reset_metadata
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_unlink_series
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_author_penciller
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_author_writers
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_edit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_genres
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_links
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_publisher
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_tab_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_tab_collections
import kotlin.math.max
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.settings.model.BooksLayout
import snd.komelia.ui.KomeliaSpacing
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomfIntegration
import snd.komelia.ui.LocalKomfMangaBakaIntegration
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.collection.SeriesCollectionsContent
import snd.komelia.ui.collection.SeriesCollectionsState
import snd.komelia.ui.common.TagList
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.common.components.DescriptionChips
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.KomeliaTopBarSurface
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.LabeledEntry.Companion.stringEntry
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.common.menus.bulk.BooksBulkActionsContent
import snd.komelia.ui.common.menus.bulk.BottomBulkActionsPanel
import snd.komelia.ui.dialogs.komf.identify.KomfIdentifyDialog
import snd.komelia.ui.dialogs.komf.mangabaka.KomfMangaBakaLinkDialog
import snd.komelia.ui.dialogs.komf.mangabaka.KomfMangaBakaUnlinkDialog
import snd.komelia.ui.dialogs.komf.reset.KomfResetSeriesMetadataDialog
import snd.komelia.ui.dialogs.series.edit.SeriesEditDialog
import snd.komelia.ui.icons.AppIcons
import snd.komelia.ui.icons.outlined.LinkedServices
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.platform.VerticalScrollbarWithFullSpans
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.series.SeriesBooksState
import snd.komelia.ui.series.SeriesBooksState.BooksData
import snd.komelia.ui.series.SeriesViewModel.SeriesTab
import snd.komelia.ui.series.mangabaka.MangaBakaSeriesContent
import snd.komelia.ui.series.mangabaka.state.MangaBakaMetadataState
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.library.KomgaLibrary

@Composable
fun SeriesContent(
    series: KomeliaSeries?,
    mangaBaka: MangaBakaMetadataState?,
    library: KomgaLibrary?,
    onLibraryClick: (KomgaLibrary) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    currentTab: SeriesTab,
    onTabChange: (SeriesTab) -> Unit,

    booksState: SeriesBooksState,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,

    collectionsState: SeriesCollectionsState,
    onCollectionClick: (KomgaCollection) -> Unit,
    onSeriesClick: (KomeliaSeries) -> Unit,

    onDownload: () -> Unit,
    onBackPress: () -> Unit,
    scrollState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    isMasterPane: Boolean = false,
) {
    val windowWidth = LocalWindowWidth.current
    val layout = LocalKomeliaLayout.current
    val gridMinWidth = booksState.cardWidth.collectAsState().value
    val width = LocalWindowWidth.current
    val booksLoadState = booksState.state.collectAsState().value
    val bookMenuActions = remember { booksState.bookMenuActions() }

    val booksData = remember(booksLoadState) {
        if (booksLoadState is LoadState.Success<BooksData>) booksLoadState.value
        else BooksData()
    }
    val pageNavigationState = remember(series?.id) { SeriesPageNavigationState() }
    val isContentScrolled by remember(scrollState) {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 0 || scrollState.firstVisibleItemScrollOffset > 0
        }
    }

    LaunchedEffect(booksLoadState, pageNavigationState.pendingPage) {
        when (booksLoadState) {
            is LoadState.Success<BooksData> -> {
                val loadedPage = booksLoadState.value.currentPage
                if (pageNavigationState.isRequestedPage(loadedPage)) {
                    scrollState.scrollToItem(if (collectionsState.collections.isEmpty()) 1 else SERIES_BOOKS_SECTION_INDEX)
                    pageNavigationState.onPageScrollCompleted(loadedPage)
                }
            }

            is LoadState.Error -> pageNavigationState.onPageLoadFailed()
            LoadState.Loading, LoadState.Uninitialized -> Unit
        }
    }

    PauseGlassWhileBusy(scrollState.isScrollInProgress)
    val compact = windowWidth == COMPACT || windowWidth == MEDIUM
    Column(Modifier.fillMaxSize()) {
        KomeliaPageScaffold(modifier = Modifier.weight(1f), topBar = {
            if (booksData.selectionMode) {
                KomeliaTopBarSurface(isContentScrolled = isContentScrolled) {
                    BooksBulkActionsToolbar(
                        onCancel = { booksState.setSelectionMode(false) },
                        books = booksData.books,
                        actions = booksState.bookBulkMenuActions(),
                        selectedBooks = booksData.selectedBooks,
                        onBookSelect = booksState::onBookSelect
                    )
                }
            } else {
                SeriesToolBar(series, seriesMenuActions, mangaBaka, onDownload, onBackPress,
                    showTitle = isContentScrolled || isMasterPane)
            }
        }) { topInset ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyVerticalGrid(
                    state = scrollState,
                    columns = snd.komelia.ui.common.itemlist.posterGridCells(gridMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
                    verticalArrangement = Arrangement.spacedBy(if (booksData.layout == BooksLayout.LIST) 0.dp else layout.gridSpacing),
                    contentPadding = PaddingValues(
                        top = (if (compact) 6.dp else layout.topBarContentSpacing) + topInset,
                        bottom = layout.gridBottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    modifier = Modifier
                        .widthIn(max = layout.contentMaxWidth)
                        .fillMaxSize()
                        .padding(horizontal = layout.pageHorizontalPadding),
                ) {

                    if (series != null && library != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val targetState = booksState.readingTarget.collectAsState().value
                            SeriesReadingHeader(
                                readingTarget = if (isMasterPane) LoadState.Success(null) else targetState,
                                onRead = { onBookReadClick(it, true) },
                                onRetry = booksState::reloadReadingTarget,
                            ) {
                                if (!isMasterPane) Series(series, library, onLibraryClick, onFilterClick)
                                else Text(pluralStringResource(Res.plurals.series_books_count, series.booksCount, series.booksCount.toString()),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val tabSelector: (@Composable () -> Unit)? = if (collectionsState.collections.isNotEmpty() && !booksData.selectionMode) {
                            { SeriesTabSelector(currentTab, series.booksCount, collectionsState.collections.size, onTabChange) }
                        } else null

                        when (currentTab) {
                            SeriesTab.BOOKS -> SeriesBooksContent(
                                series = series,
                                onBookClick = onBookClick,
                                onBookReadClick = onBookReadClick,
                                booksLoadState = booksLoadState,
                                onBooksLayoutChange = booksState::onBookLayoutChange,
                                onBooksPageSizeChange = booksState::onBookPageSizeChange,
                                onPageChange = { page ->
                                    pageNavigationState.request(page, booksState::onPageChange)
                                },
                                onBookSelect = booksState::onBookSelect,
                                booksFilterState = booksState.filterState,
                                bookContextMenuActions = bookMenuActions,
                                isMasterPane = isMasterPane,
                                tabSelector = tabSelector,
                            )

                            SeriesTab.COLLECTIONS -> item(span = { GridItemSpan(maxLineSpan) }) {
                                Column {
                                tabSelector?.invoke()
                                SeriesCollectionsContent(
                                    collections = collectionsState.collections,
                                    onCollectionClick = onCollectionClick,
                                    onSeriesClick = onSeriesClick,
                                    cardWidth = collectionsState.cardWidth.collectAsState().value
                                )
                                }
                            }
                        }
                    }

                    if (series != null && library != null) item(span = { GridItemSpan(maxLineSpan) }) {
                        var expanded by rememberSaveable(series.id.value) { mutableStateOf(false) }
                        Column {
                            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(Res.string.reading_file_source))
                            }
                            if (expanded) {
                                KomfSeriesActionsButton(series)
                                if (mangaBaka != null) MangaBakaSeriesContent(series, seriesMenuActions, library, mangaBaka, onLibraryClick)
                                else {
                                    SeriesDescriptionRow(library, onLibraryClick, series.booksMetadata.releaseDate,
                                        series.metadata.status, series.metadata.ageRating, series.metadata.language,
                                        series.metadata.readingDirection, series.deleted || library.unavailable,
                                        series.metadata.alternateTitles, onFilterClick, Modifier)
                                    SeriesSummary(series.metadata.summary, series.booksMetadata.summary, series.booksMetadata.summaryNumber)
                                    SeriesChipTags(series, onFilterClick)
                                }
                            }
                        }
                    }
                }

                val gridFullSpansCount = remember(currentTab, booksLoadState) {
                    if (currentTab == SeriesTab.COLLECTIONS) return@remember 3

                    val fullSpans = when (booksLoadState) {
                        is LoadState.Success<BooksData> -> if (booksLoadState.value.totalPages > 1) 4 else 3
                        else -> 2
                    }
                    when (booksData.layout) {
                        BooksLayout.GRID -> fullSpans
                        BooksLayout.LIST -> fullSpans + booksData.books.size
                    }
                }
                VerticalScrollbarWithFullSpans(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    fullSpanLines = gridFullSpansCount,
                )
            }
        }

        if (currentTab == SeriesTab.BOOKS &&
            (width == COMPACT || width == MEDIUM) && booksData.selectedBooks.isNotEmpty()
        ) {
            BottomBulkActionsPanel {
                BooksBulkActionsContent(
                    books = booksData.selectedBooks,
                    actions = booksState.bookBulkMenuActions(),
                    compact = true
                )
            }
        }
    }
}

internal const val SERIES_BOOKS_SECTION_INDEX = 2

internal class SeriesPageNavigationState {
    var pendingPage by mutableStateOf<Int?>(null)
        private set

    fun request(page: Int, onPageChange: (Int) -> Unit) {
        pendingPage = page
        onPageChange(page)
    }

    fun isRequestedPage(page: Int): Boolean = pendingPage == page

    fun onPageScrollCompleted(page: Int) {
        if (pendingPage == page) pendingPage = null
    }

    fun onPageLoadFailed() {
        pendingPage = null
    }
}

@Composable
fun SeriesToolBar(
    series: KomeliaSeries?,
    seriesMenuActions: SeriesMenuActions,
    mangaBaka: MangaBakaMetadataState?,
    onDownload: () -> Unit,
    onBackPress: () -> Unit,
    floating: Boolean = false,
    showTitle: Boolean = true,
) {
    val layout = LocalKomeliaLayout.current
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingChromeIconButton(onClick = onBackPress, floating = floating) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.navigation_back),
            )
        }

        if (series != null) {
            Text(
                if (showTitle) mangaBaka?.titleState?.mainTitle?.title ?: series.metadata.title else stringResource(Res.string.reading_series_title),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )

            Box {
                var expandActions by remember { mutableStateOf(false) }
                FloatingChromeIconButton(onClick = { expandActions = true }, floating = floating) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.actions_show_more))
                }

                SeriesActionsMenu(
                    series = series,
                    actions = seriesMenuActions,
                    expanded = expandActions,
                    showEditOption = true,
                    showDownloadOption = true,
                    onDismissRequest = { expandActions = false },
                )
            }


        }
    }
}

@Composable
fun SeriesActionMenuButton(
    series: KomeliaSeries,
    seriesMenuActions: SeriesMenuActions
) {
    Box {
        var expandActions by remember { mutableStateOf(false) }
        IconButton(onClick = { expandActions = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.actions_show_more))
        }

        SeriesActionsMenu(
            series = series,
            actions = seriesMenuActions,
            expanded = expandActions,
            showEditOption = false,
            showDownloadOption = true,
            onDismissRequest = { expandActions = false },
        )
    }
}

@Composable
fun SeriesEditDialogButton(series: KomeliaSeries) {
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    var showEditDialog by remember { mutableStateOf(false) }
    if (isAdmin && !isOffline) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            state = rememberTooltipState(),
            tooltip = { PlainTooltip { Text(stringResource(Res.string.series_edit)) } }
        ) {
            IconButton(onClick = { showEditDialog = true }) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(Res.string.series_edit))
            }
        }
    }
    if (showEditDialog)
        SeriesEditDialog(series = series, onDismissRequest = { showEditDialog = false })

}

@Composable
fun KomfSeriesActionsButton(series: KomeliaSeries) {
    val komfIntegration = LocalKomfIntegration.current.collectAsState(false)
    val isOffline = LocalOfflineMode.current.collectAsState().value
    if (!komfIntegration.value || isOffline || series.libraryId.isLocalLibrary()) return

    var showDropdown by remember { mutableStateOf(false) }
    var showKomfDialog by remember { mutableStateOf(false) }
    if (showKomfDialog) {
        KomfIdentifyDialog(
            series = series,
            onDismissRequest = {
                showKomfDialog = false
                showDropdown = false
            }
        )
    }

    var showKomfResetDialog by remember { mutableStateOf(false) }
    if (showKomfResetDialog) {
        KomfResetSeriesMetadataDialog(
            series = series,
            onDismissRequest = {
                showKomfResetDialog = false
                showDropdown = false
            }
        )
    }

    var showMangaBakaLinkDialog by remember { mutableStateOf(false) }
    if (showMangaBakaLinkDialog) {
        KomfMangaBakaLinkDialog(
            series = series,
            onDismissRequest = {
                showMangaBakaLinkDialog = false
                showDropdown = false
            }
        )
    }
    var showMangaBakaUnlinkDialog by remember { mutableStateOf(false) }
    if (showMangaBakaUnlinkDialog) {
        KomfMangaBakaUnlinkDialog(
            series = series,
            onDismissRequest = {
                showMangaBakaUnlinkDialog = false
                showDropdown = false
            }
        )
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        state = rememberTooltipState(),
        tooltip = { PlainTooltip { Text(stringResource(Res.string.mangabaka_actions)) } }
    ) {
        IconButton(onClick = { showDropdown = true }) {
            Icon(AppIcons.Outlined.LinkedServices, contentDescription = null)
        }

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            val komfMangaBakaIntegration = LocalKomfMangaBakaIntegration.current.collectAsState(false).value

            if (series.mangaBakaMetadata == null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.mangabaka_identify)) },
                    onClick = { showKomfDialog = true },
                )

                if (komfMangaBakaIntegration) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.mangabaka_link_series)) },
                        onClick = { showMangaBakaLinkDialog = true },
                    )
                }

                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.mangabaka_reset_metadata)) },
                    onClick = { showKomfResetDialog = true },
                )
            } else if (komfMangaBakaIntegration) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.mangabaka_link_series)) },
                    onClick = { showMangaBakaLinkDialog = true },
                )

                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.mangabaka_unlink_series)) },
                    onClick = { showMangaBakaUnlinkDialog = true },
                )
            }
        }
    }
}

@Composable
fun Series(
    series: KomeliaSeries,
    library: KomgaLibrary,
    onLibraryClick: (KomgaLibrary) -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
) {
    val width = LocalWindowWidth.current
    val coverWidth = if (width == COMPACT || width == MEDIUM) 76.dp else 112.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        SeriesThumbnail(series.id, modifier = Modifier.width(coverWidth).aspectRatio(2f / 3f)
            .clip(MaterialTheme.shapes.small), contentScale = ContentScale.Fit)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(series.metadata.title, style = MaterialTheme.typography.titleLarge)
            val authors = remember(series.booksMetadata.authors) {
                series.booksMetadata.authors.filter { it.role == "writer" }.map { it.name }.distinct().joinToString("、")
            }
            if (authors.isNotBlank()) Text(authors, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(pluralStringResource(Res.plurals.series_books_count, series.booksCount, series.booksCount.toString()),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (series.metadata.summary.isNotBlank()) Text(series.metadata.summary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SeriesChipTags(
    series: KomeliaSeries,
    onFilterClick: (SeriesScreenFilter) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.extraSmall)
    ) {
        if (series.metadata.publisher.isNotBlank()) {
            DescriptionChips(
                label = stringResource(Res.string.series_publisher),
                chipValue = stringEntry(series.metadata.publisher),
                onClick = { onFilterClick(SeriesScreenFilter(publisher = listOf(it))) },
            )
        }

        DescriptionChips(
            label = stringResource(Res.string.series_genres),
            chipValues = series.metadata.genres.map { stringEntry(it) },
            onChipClick = { onFilterClick(SeriesScreenFilter(genres = listOf(it))) },
        )

        TagList(
            tags = series.metadata.tags,
            secondaryTags = series.booksMetadata.tags,
            onTagClick = { onFilterClick(SeriesScreenFilter(tags = listOf(it))) },
        )

        val uriHandler = LocalUriHandler.current
        DescriptionChips(
            label = stringResource(Res.string.series_links),
            chipValues = series.metadata.links.map { LabeledEntry(it, it.label) },
            onChipClick = { entry -> uriHandler.openUri(entry.url) },
            icon = Icons.Rounded.Link,
        )

        series.booksMetadata.authors
            .filter { it.role == "writer" }
            .groupBy { it.role }
            .forEach { (_, author) ->
                DescriptionChips(
                    label = stringResource(Res.string.series_author_writers),
                    chipValues = author.map { LabeledEntry(it, it.name) },
                    onChipClick = { onFilterClick(SeriesScreenFilter(authors = listOf(it))) },
                    modifier = Modifier.cursorForHand()
                )
            }

        series.booksMetadata.authors
            .filter { it.role == "penciller" }
            .groupBy { it.role }
            .forEach { (_, author) ->
                DescriptionChips(
                    label = stringResource(Res.string.series_author_penciller),
                    chipValues = author.map { LabeledEntry(it, it.name) },
                    onChipClick = { onFilterClick(SeriesScreenFilter(authors = listOf(it))) },
                    modifier = Modifier.cursorForHand()
                )
            }
    }
}

@Composable
private fun SeriesTabSelector(currentTab: SeriesTab, booksCount: Int, collectionsCount: Int, onTabChange: (SeriesTab) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val booksLabel = stringResource(Res.string.series_tab_books)
    val collectionsLabel = stringResource(Res.string.series_tab_collections)
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (currentTab == SeriesTab.BOOKS) "$booksLabel · $booksCount" else "$collectionsLabel · $collectionsCount",
                style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
        }
        snd.komelia.ui.common.components.KomeliaDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf(SeriesTab.BOOKS to booksLabel, SeriesTab.COLLECTIONS to collectionsLabel).forEach { (tab, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onTabChange(tab); expanded = false })
            }
        }
    }
}
