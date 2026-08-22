package snd.komelia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_search_groups
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.posterColumnCount
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komga.client.series.KomgaSeries

@Composable
fun HomeContent(
    filters: List<HomeFilterData>,
    activeFilterNumber: Int,
    onFilterChange: (Int) -> Unit,

    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    Column {
        Toolbar(
            filters = filters,
            currentFilterNumber = activeFilterNumber,
            onFilterChange = {
                onFilterChange(it)
                coroutineScope.launch { gridState.animateScrollToItem(0) }
            },
        )
        DisplayContent(
            filters = filters,
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
    currentFilterNumber: Int,
    onFilterChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val nonEmptyFilters = remember(filters) {
        filters.filter {
            when (it) {
                is BookFilterData -> it.books.isNotEmpty()
                is SeriesFilterData -> it.series.isNotEmpty()
            }
        }
    }
    if (nonEmptyFilters.size <= 1) return

    val windowWidth = LocalWindowWidth.current
    val useBottomSheet = windowWidth == snd.komelia.ui.platform.WindowSizeClass.COMPACT ||
            windowWidth == snd.komelia.ui.platform.WindowSizeClass.MEDIUM
    val currentFilter = nonEmptyFilters.firstOrNull { it.filter.order == currentFilterNumber }
    var pickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(currentFilterNumber, nonEmptyFilters) {
        if (currentFilterNumber != 0 && currentFilter == null) onFilterChange(0)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = layout.pageHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            onClick = { onFilterChange(0) },
            selected = currentFilterNumber == 0,
            label = { Text(stringResource(Res.string.home_filter_all)) },
            colors = chipColors,
            border = null,
        )
        currentFilter?.let { data ->
            FilterChip(
                onClick = {},
                selected = true,
                label = { Text(data.filter.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = chipColors,
                border = null,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Box {
            FilterChip(
                onClick = { pickerOpen = true },
                selected = false,
                leadingIcon = { Icon(Icons.Rounded.MoreHoriz, null) },
                label = { Text(stringResource(Res.string.home_filter_more)) },
                colors = chipColors,
                border = null,
            )
            if (!useBottomSheet) {
                DropdownMenu(
                    expanded = pickerOpen,
                    onDismissRequest = { pickerOpen = false },
                ) {
                    HomeGroupPickerItems(
                        filters = nonEmptyFilters,
                        currentFilterNumber = currentFilterNumber,
                        onFilterChange = {
                            pickerOpen = false
                            onFilterChange(it)
                        },
                    )
                }
            }
        }
    }

    if (useBottomSheet && pickerOpen) {
        HomeGroupPickerSheet(
            filters = nonEmptyFilters,
            currentFilterNumber = currentFilterNumber,
            onDismiss = { pickerOpen = false },
            onFilterChange = {
                pickerOpen = false
                onFilterChange(it)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeGroupPickerSheet(
    filters: List<HomeFilterData>,
    currentFilterNumber: Int,
    onDismiss: () -> Unit,
    onFilterChange: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.home_filter_groups), style = MaterialTheme.typography.titleLarge)
            HomeGroupPickerItems(filters, currentFilterNumber, onFilterChange)
        }
    }
}

@Composable
private fun HomeGroupPickerItems(
    filters: List<HomeFilterData>,
    currentFilterNumber: Int,
    onFilterChange: (Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visibleFilters = remember(filters, query) {
        if (query.isBlank()) filters else filters.filter { it.filter.label.contains(query, ignoreCase = true) }
    }
    Column(Modifier.width(320.dp).heightIn(max = 480.dp)) {
        if (filters.size > 6) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(Res.string.home_filter_search_groups)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        LazyColumn {
            items(visibleFilters.size) { index ->
                val data = visibleFilters[index]
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(data.filter.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(Res.string.home_filter_group_items, data.itemCount()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (data.filter.order == currentFilterNumber) {
                        { Icon(Icons.Rounded.Check, null) }
                    } else null,
                    onClick = { onFilterChange(data.filter.order) },
                )
            }
        }
    }
}

private fun HomeFilterData.itemCount(): Int = when (this) {
    is BookFilterData -> books.size
    is SeriesFilterData -> series.size
}

@Composable
private fun DisplayContent(
    filters: List<HomeFilterData>,
    activeFilterNumber: Int,
    gridState: LazyGridState,
    cardWidth: Dp,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val fixedColumnCount = posterColumnCount(LocalPlatform.current, LocalWindowWidth.current)
    LazyVerticalGrid(
        modifier = Modifier.padding(horizontal = layout.pageHorizontalPadding),
        state = gridState,
        columns = fixedColumnCount?.let(GridCells::Fixed) ?: GridCells.Adaptive(cardWidth),
        horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = layout.gridBottomPadding + 16.dp,
        )
    ) {
        for (data in filters) {
            if (activeFilterNumber == 0 || data.filter.order == activeFilterNumber) {
                when (data) {
                    is BookFilterData -> BookFilterEntry(
                        label = data.filter.label,
                        books = data.books,
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

private fun LazyGridScope.BookFilterEntry(
    label: String,
    books: List<KomeliaBook>,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    if (books.isEmpty()) return

    item(span = { GridItemSpan(maxLineSpan) }) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            HorizontalDivider()
        }
    }
    items(books) { book ->
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
    series: List<KomgaSeries>,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
) {
    if (series.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            HorizontalDivider()
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
