package snd.komelia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import snd.komelia.ui.common.components.KomeliaDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import snd.komelia.ui.common.components.KomeliaModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_all
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_group_items
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_groups
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_search_groups
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_default_recently_read_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_home
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.BrowsePageHeader
import snd.komelia.ui.common.components.komeliaHighlight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.PlatformType

@Composable
internal fun HomeContent(
    filters: List<HomeFilterData>,
    activeFilterNumber: Int,
    onFilterChange: (Int) -> Unit,
    onSearchClick: () -> Unit,

    cardWidth: Dp,
    onSeriesClick: (KomeliaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    recentlyReadBooks: List<KomeliaBook> = emptyList(),
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    PauseGlassWhileBusy(gridState.isScrollInProgress)
    KomeliaPageScaffold(topBar = {
        Toolbar(
            filters = filters,
            recentlyReadBooks = recentlyReadBooks,
            currentFilterNumber = activeFilterNumber,
            onSearchClick = onSearchClick,
            onFilterChange = {
                onFilterChange(it)
                coroutineScope.launch { gridState.animateScrollToItem(0) }
            },
        )
    }) { topInset ->
        DisplayContent(
            topInset = topInset,
            filters = filters,
            recentlyReadBooks = recentlyReadBooks,
            activeFilterNumber = activeFilterNumber,

            gridState = gridState,
            cardWidth = cardWidth,
            onSeriesClick = onSeriesClick,
            seriesMenuActions = seriesMenuActions,
            bookMenuActions = bookMenuActions,
            onBookClick = onBookClick,
            onBookReadClick = onBookReadClick,
        )
    }
}

@Composable
private fun Toolbar(
    filters: List<HomeFilterData>,
    recentlyReadBooks: List<KomeliaBook>,
    currentFilterNumber: Int,
    onSearchClick: () -> Unit,
    onFilterChange: (Int) -> Unit,
) {
    val toolbarFilters = remember(filters) { homeToolbarEntries(filters) }
    val allLabel = stringResource(Res.string.home_filter_all)
    val entries = listOf(HomeToolbarEntry(HOME_ALL_TAB_ID, allLabel, homeAllItemCount(filters, recentlyReadBooks))) + toolbarFilters
    val currentFilter = toolbarFilters.firstOrNull { it.id == currentFilterNumber }
    var pickerOpen by remember { mutableStateOf(false) }
    val useBottomSheet = LocalWindowWidth.current == snd.komelia.ui.platform.WindowSizeClass.COMPACT ||
        LocalWindowWidth.current == snd.komelia.ui.platform.WindowSizeClass.MEDIUM
    LaunchedEffect(currentFilterNumber, toolbarFilters) {
        if (currentFilterNumber != 0 && toolbarFilters.none { it.id == currentFilterNumber }) onFilterChange(0)
    }
    BrowsePageHeader(
        title = currentFilter?.label ?: stringResource(Res.string.navbar_home),
        onSearchClick = if (LocalPlatform.current != PlatformType.DESKTOP) onSearchClick else null,
    ) {
        if (toolbarFilters.isNotEmpty()) {
            Box {
                IconButton(onClick = { pickerOpen = true }) {
                    Icon(Icons.Rounded.Tune, stringResource(Res.string.home_filter_groups))
                }
                if (!useBottomSheet) DropdownMenu(pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    HomeGroupPickerItems(entries, currentFilterNumber, Modifier.width(320.dp)) {
                        pickerOpen = false
                        onFilterChange(it)
                    }
                }
            }
        }
    }
    if (useBottomSheet && pickerOpen) HomeGroupPickerSheet(entries, currentFilterNumber,
        onDismiss = { pickerOpen = false }, onFilterChange = {
            pickerOpen = false
            onFilterChange(it)
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeGroupPickerSheet(
    filters: List<HomeToolbarEntry>,
    currentFilterNumber: Int,
    onDismiss: () -> Unit,
    onFilterChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(
                horizontal = layout.dialogContentPadding,
                vertical = layout.controlSpacing,
            ),
            verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        ) {
            Text(stringResource(Res.string.home_filter_groups), style = MaterialTheme.typography.titleLarge)
            HomeGroupPickerItems(
                filters = filters,
                currentFilterNumber = currentFilterNumber,
                modifier = Modifier.fillMaxWidth(),
                onFilterChange = onFilterChange,
            )
        }
    }
}

@Composable
internal fun HomeGroupPickerItems(
    filters: List<HomeToolbarEntry>,
    currentFilterNumber: Int,
    modifier: Modifier = Modifier,
    onFilterChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    var query by remember { mutableStateOf("") }
    val visibleFilters = remember(filters, query) {
        if (query.isBlank()) filters else filters.filter { it.label.contains(query, ignoreCase = true) }
    }
    Column(modifier.heightIn(max = 480.dp)) {
        if (filters.size > 6) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(Res.string.home_filter_search_groups)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = layout.controlSpacing),
            )
        }
        // DropdownMenu measures intrinsic width. A regular bounded scroll column also works
        // in the compact sheet, where LazyColumn cannot answer those menu measurements.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            visibleFilters.forEach { data ->
                DropdownMenuItem(
                    modifier = Modifier.komeliaHighlight(data.id == currentFilterNumber, MaterialTheme.shapes.small, inset = 4.dp)
                        .semantics { selected = data.id == currentFilterNumber },
                    text = {
                        Column {
                            Text(data.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(Res.string.home_filter_group_items, data.itemCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (data.id == currentFilterNumber) {
                        { Icon(Icons.Rounded.Check, null) }
                    } else null,
                    onClick = { onFilterChange(data.id) },
                )
            }
        }
    }
}

private fun HomeFilterData.itemCount(): Int = when (this) {
    is BookFilterData -> books.size
    is SeriesFilterData -> series.size
}

internal fun homeAllItemCount(filters: List<HomeFilterData>, recentlyReadBooks: List<KomeliaBook>): Int {
    if (recentlyReadBooks.isEmpty()) return filters.sumOf { it.itemCount() }
    val hero = mostRecentlyReadingBook(recentlyReadBooks) ?: homeFeaturedBook(filters, HOME_ALL_TAB_ID)?.book
    val extraHero = if (hero != null && recentlyReadBooks.none { it.id == hero.id }) 1 else 0
    return recentlyReadBooks.size + extraHero +
        filters.filterNot { isReadingOverviewFilter(it.filter) }.sumOf { it.itemCount() }
}

internal fun homeGroupToolbarFilters(filters: List<HomeFilterData>): List<HomeFilterData> =
    filters.sortedBy { it.filter.order }

internal const val HOME_ALL_TAB_ID = 0

internal data class HomeToolbarEntry(
    val id: Int,
    val label: String,
    val itemCount: Int,
)

internal fun homeToolbarEntries(
    filters: List<HomeFilterData>,
): List<HomeToolbarEntry> = homeGroupToolbarFilters(filters).map {
    HomeToolbarEntry(it.filter.order, it.filter.label, it.itemCount())
}

@Composable
private fun DisplayContent(
    topInset: Dp,
    filters: List<HomeFilterData>,
    recentlyReadBooks: List<KomeliaBook>,
    activeFilterNumber: Int,
    gridState: LazyGridState,
    cardWidth: Dp,
    onSeriesClick: (KomeliaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val columns = snd.komelia.ui.common.itemlist.posterGridCells(cardWidth)
    val featured = remember(filters, activeFilterNumber) { homeFeaturedBook(filters, activeFilterNumber) }
    val recentLabel = stringResource(Res.string.home_filter_default_recently_read_books)
    val recentFeatured = remember(recentlyReadBooks) { mostRecentlyReadingBook(recentlyReadBooks) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontalPadding),
            state = gridState,
            columns = columns,
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            contentPadding = PaddingValues(
                top = layout.topBarContentSpacing + topInset,
                bottom = layout.gridBottomPadding + layout.sectionSpacing,
            )
        ) {
            if (activeFilterNumber == HOME_ALL_TAB_ID && recentlyReadBooks.isNotEmpty()) {
                BookFilterEntry(
                    label = recentLabel,
                    books = recentlyReadBooks,
                    featuredBook = recentFeatured ?: featured?.book,
                    bookMenuActions = bookMenuActions,
                    onBookClick = onBookClick,
                    onBookReadClick = onBookReadClick,
                )
            }
            for (data in filters) {
                if (activeFilterNumber == HOME_ALL_TAB_ID || data.filter.order == activeFilterNumber) {
                    if (activeFilterNumber == HOME_ALL_TAB_ID && recentlyReadBooks.isNotEmpty() && isReadingOverviewFilter(data.filter)) continue
                    when (data) {
                        is BookFilterData -> BookFilterEntry(
                            label = data.filter.label,
                            books = data.books,
                            featuredBook = featured?.takeIf {
                                it.groupOrder == data.filter.order &&
                                    (activeFilterNumber != HOME_ALL_TAB_ID || recentlyReadBooks.isEmpty())
                            }?.book,
                            bookMenuActions = bookMenuActions,
                            onBookClick = onBookClick,
                            onBookReadClick = onBookReadClick,
                        )

                        is SeriesFilterData -> SeriesFilterEntries(
                            label = data.filter.label,
                            series = data.series,
                            onSeriesClick = onSeriesClick,
                            seriesMenuActions = seriesMenuActions,
                        )

                    }
                }
            }
        }
    }
}

private fun LazyGridScope.BookFilterEntry(
    label: String,
    books: List<KomeliaBook>,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    featuredBook: KomeliaBook? = null,
) {
    if (books.isEmpty()) return

    if (featuredBook != null) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "hero-${featuredBook.id.value}") {
            ContinueReadingHero(
                ContinueReadingHeroContent(label, featuredBook),
                ContinueReadingHeroActions(
                    onDetails = { onBookClick(featuredBook) },
                    onRead = { onBookReadClick(featuredBook, true) },
                    menu = bookMenuActions,
                ),
            )
        }
    } else item(span = { GridItemSpan(maxLineSpan) }) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.controlSpacing),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
    items(books.filterNot { it.id == featuredBook?.id }) { book ->
        BookImageCard(
            book = book,
            onBookClick = { onBookClick(book) },
            onBookReadClick = { onBookReadClick(book, it) },
            bookMenuActions = bookMenuActions,
            showSeriesTitle = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun LazyGridScope.SeriesFilterEntries(
    label: String,
    series: List<KomeliaSeries>,
    onSeriesClick: (KomeliaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
) {
    if (series.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    items(series) {
        SeriesImageCard(
            series = it,
            onSeriesClick = { onSeriesClick(it) },
            seriesMenuActions = seriesMenuActions,
            modifier = Modifier.fillMaxSize()
        )
    }
}
