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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.layout.SubcomposeLayout
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

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AdaptiveHomeGroupBar(
            filters = nonEmptyFilters,
            currentFilterNumber = currentFilterNumber,
            chipColors = chipColors,
            useBottomSheet = useBottomSheet,
            pickerOpen = pickerOpen,
            onPickerOpenChange = { pickerOpen = it },
            onFilterChange = onFilterChange,
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = layout.pageHorizontalPadding),
        )
    }
}

private enum class HomeGroupBarSlot { All, MoreProbe, More }
private data class HomeGroupProbeSlot(val index: Int)
private data class HomeGroupConstrainedSlot(val index: Int)

@Composable
private fun AdaptiveHomeGroupBar(
    filters: List<HomeFilterData>,
    currentFilterNumber: Int,
    chipColors: androidx.compose.material3.SelectableChipColors,
    useBottomSheet: Boolean,
    pickerOpen: Boolean,
    onPickerOpenChange: (Boolean) -> Unit,
    onFilterChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalKomeliaLayout.current
    val allLabel = stringResource(Res.string.home_filter_all)
    val moreLabel = stringResource(Res.string.home_filter_more)
    val spacing = layout.controlSpacing

    SubcomposeLayout(modifier = modifier) { constraints ->
        val chipConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val allPlaceable = subcompose(HomeGroupBarSlot.All) {
            HomeGroupChip(
                label = allLabel,
                selected = currentFilterNumber == 0,
                colors = chipColors,
                minimumHeight = layout.minimumTouchTarget,
                onClick = { onFilterChange(0) },
            )
        }.single().measure(chipConstraints)
        val moreProbe = subcompose(HomeGroupBarSlot.MoreProbe) {
            HomeMoreChip(
                label = moreLabel,
                colors = chipColors,
                minimumHeight = layout.minimumTouchTarget,
                onClick = {},
            )
        }.single().measure(chipConstraints)
        val groupPlaceables = filters.mapIndexed { index, data ->
            subcompose(HomeGroupProbeSlot(index)) {
                HomeGroupChip(
                    label = data.filter.label,
                    selected = data.filter.order == currentFilterNumber,
                    colors = chipColors,
                    minimumHeight = layout.minimumTouchTarget,
                    onClick = { onFilterChange(data.filter.order) },
                )
            }.single().measure(chipConstraints)
        }
        val activeIndex = filters.indexOfFirst { it.filter.order == currentFilterNumber }.takeIf { it >= 0 }
        val spacingPx = spacing.roundToPx()
        val result = calculateHomeGroupOverflowLayout(
            availableWidth = constraints.maxWidth,
            allChipWidth = allPlaceable.width,
            moreChipWidth = moreProbe.width,
            groupWidths = groupPlaceables.map { it.width },
            activeGroupIndex = activeIndex,
            spacing = spacingPx,
        )
        val overflowFilters = result.overflowGroupIndices.map(filters::get)
        val promotedCapacity = (
                constraints.maxWidth - allPlaceable.width - moreProbe.width - spacingPx * 2
                ).coerceAtLeast(0)
        val visibleGroupPlaceables = result.visibleGroupIndices.map { index ->
            if (index == activeIndex && groupPlaceables[index].width > promotedCapacity) {
                subcompose(HomeGroupConstrainedSlot(index)) {
                    HomeGroupChip(
                        label = filters[index].filter.label,
                        selected = true,
                        colors = chipColors,
                        minimumHeight = layout.minimumTouchTarget,
                        onClick = { onFilterChange(filters[index].filter.order) },
                    )
                }.single().measure(
                    chipConstraints.copy(maxWidth = promotedCapacity.coerceAtLeast(1)),
                )
            } else {
                groupPlaceables[index]
            }
        }
        val morePlaceable = if (overflowFilters.isNotEmpty()) {
            subcompose(HomeGroupBarSlot.More) {
                HomeMorePicker(
                    label = moreLabel,
                    filters = overflowFilters,
                    currentFilterNumber = currentFilterNumber,
                    colors = chipColors,
                    minimumHeight = layout.minimumTouchTarget,
                    useBottomSheet = useBottomSheet,
                    pickerOpen = pickerOpen,
                    onPickerOpenChange = onPickerOpenChange,
                    onFilterChange = onFilterChange,
                )
            }.single().measure(chipConstraints)
        } else null

        val placeables = buildList {
            add(allPlaceable)
            addAll(visibleGroupPlaceables)
            morePlaceable?.let(::add)
        }
        val contentHeight = placeables.maxOfOrNull { it.height } ?: 0
        val width = constraints.maxWidth.coerceAtLeast(constraints.minWidth)
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += placeable.width + spacingPx
            }
        }
    }
}

@Composable
private fun HomeGroupChip(
    label: String,
    selected: Boolean,
    colors: androidx.compose.material3.SelectableChipColors,
    minimumHeight: Dp,
    onClick: () -> Unit,
) {
    FilterChip(
        onClick = onClick,
        selected = selected,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = colors,
        border = null,
        modifier = Modifier.heightIn(min = minimumHeight),
    )
}

@Composable
private fun HomeMoreChip(
    label: String,
    colors: androidx.compose.material3.SelectableChipColors,
    minimumHeight: Dp,
    onClick: () -> Unit,
) {
    FilterChip(
        onClick = onClick,
        selected = false,
        leadingIcon = { Icon(Icons.Rounded.MoreHoriz, null) },
        label = { Text(label, maxLines = 1) },
        colors = colors,
        border = null,
        modifier = Modifier.heightIn(min = minimumHeight),
    )
}

@Composable
private fun HomeMorePicker(
    label: String,
    filters: List<HomeFilterData>,
    currentFilterNumber: Int,
    colors: androidx.compose.material3.SelectableChipColors,
    minimumHeight: Dp,
    useBottomSheet: Boolean,
    pickerOpen: Boolean,
    onPickerOpenChange: (Boolean) -> Unit,
    onFilterChange: (Int) -> Unit,
) {
    Box {
        HomeMoreChip(label, colors, minimumHeight) { onPickerOpenChange(true) }
        if (!useBottomSheet) {
            DropdownMenu(
                expanded = pickerOpen,
                onDismissRequest = { onPickerOpenChange(false) },
            ) {
                HomeGroupPickerItems(
                    filters = filters,
                    currentFilterNumber = currentFilterNumber,
                    modifier = Modifier.width(320.dp),
                    onFilterChange = {
                        onPickerOpenChange(false)
                        onFilterChange(it)
                    },
                )
            }
        }
    }
    if (useBottomSheet && pickerOpen) {
        HomeGroupPickerSheet(
            filters = filters,
            currentFilterNumber = currentFilterNumber,
            onDismiss = { onPickerOpenChange(false) },
            onFilterChange = {
                onPickerOpenChange(false)
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
private fun HomeGroupPickerItems(
    filters: List<HomeFilterData>,
    currentFilterNumber: Int,
    modifier: Modifier = Modifier,
    onFilterChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    var query by remember { mutableStateOf("") }
    val visibleFilters = remember(filters, query) {
        if (query.isBlank()) filters else filters.filter { it.filter.label.contains(query, ignoreCase = true) }
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontalPadding),
            state = gridState,
            columns = fixedColumnCount?.let(GridCells::Fixed) ?: GridCells.Adaptive(cardWidth),
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            contentPadding = PaddingValues(
                top = layout.controlSpacing,
                bottom = layout.gridBottomPadding + layout.sectionSpacing,
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
