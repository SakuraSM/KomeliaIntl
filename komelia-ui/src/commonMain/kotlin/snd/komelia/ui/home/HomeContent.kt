package snd.komelia.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.homePosterColumnCount
import snd.komga.client.series.KomgaSeries

// Keep the collapsed preview balanced for the two-column mobile home layout.
private const val HOME_FILTER_PREVIEW_COUNT = 4

@Composable
fun HomeContent(
    filters: List<HomeFilterData>,
    onEditStart: () -> Unit,

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
            onEditStart = onEditStart,
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
    onEditStart: () -> Unit
) {
    val strings = LocalStrings.current.legacy
    val layout = LocalKomeliaLayout.current
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )
    val nonEmptyFilters = remember(filters) {
        filters.filter {
            when (it) {
                is BookFilterData -> it.books.isNotEmpty()
                is SeriesFilterData -> it.series.isNotEmpty()
            }
        }
    }
    Box {
        val motion = LocalKomeliaMotion.current
        val lazyRowState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LazyRow(
            state = lazyRowState,
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = motion.duration(motion.contentDurationMillis),
                    easing = motion.standardEasing,
                )
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                Spacer(Modifier.width(layout.pageHorizontalPadding))
            }

            item {
                FilterChip(
                    onClick = onEditStart,
                    selected = false,
                    label = {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = strings.forText("Edit home filters"),
                        )
                    },
                    colors = chipColors,
                    border = null,
                )
            }

            if (filters.size > 1) {
                item {
                    FilterChip(
                        onClick = { onFilterChange(0) },
                        selected = currentFilterNumber == 0,
                        label = { Text(snd.komelia.ui.LocalStrings.current.legacy.forText("All")) },
                        colors = chipColors,
                        border = null,
                    )
                }
            }
            items(nonEmptyFilters) { data ->
                val display = remember(data.filter) {
                    when (data) {
                        is BookFilterData -> data.books.isNotEmpty()
                        is SeriesFilterData -> data.series.isNotEmpty()
                    }
                }
                if (display) {
                    FilterChip(
                        onClick = { onFilterChange(data.filter.order) },
                        selected = currentFilterNumber == data.filter.order || filters.size == 1,
                        label = { Text(strings.forText(data.filter.label)) },
                        colors = chipColors,
                        border = null,
                    )
                }
            }
            item {
                Spacer(Modifier.width(layout.pageHorizontalPadding))
            }
        }

        if (LocalPlatform.current != PlatformType.MOBILE) {
            Row {
                if (lazyRowState.canScrollBackward) {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { coroutineScope.launch { lazyRowState.animateScrollBy(-200.0f) } },
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = strings.forText("Scroll left"))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (lazyRowState.canScrollForward) {
                    IconButton(
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { coroutineScope.launch { lazyRowState.animateScrollBy(200.0f) } },
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = strings.forText("Scroll right"))
                    }
                }
            }
        }
    }
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
    val expandedFilterOrders = remember { mutableStateMapOf<Int, Boolean>() }
    val layout = LocalKomeliaLayout.current
    val fixedColumnCount = homePosterColumnCount(LocalPlatform.current, LocalWindowWidth.current)
    LazyVerticalGrid(
        modifier = Modifier.padding(horizontal = layout.pageHorizontalPadding),
        state = gridState,
        columns = fixedColumnCount?.let { GridCells.Fixed(it) } ?: GridCells.Adaptive(cardWidth),
        horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
        verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
        contentPadding = PaddingValues(bottom = layout.gridBottomPadding)
    ) {
        for (data in filters) {
            if (activeFilterNumber == 0 || data.filter.order == activeFilterNumber) {
                val filterOrder = data.filter.order
                val isExpanded = expandedFilterOrders[filterOrder] == true
                val onExpandedChange = {
                    expandedFilterOrders[filterOrder] = expandedFilterOrders[filterOrder] != true
                }
                when (data) {
                    is BookFilterData -> BookFilterEntry(
                        label = data.filter.label,
                        books = data.books,
                        isExpanded = isExpanded,
                        onExpandedChange = onExpandedChange,
                        bookMenuActions = bookMenuActions,
                        onBookClick = onBookClick,
                        onBookReadClick = onBookReadClick,
                    )

                    is SeriesFilterData -> SeriesFilterEntries(
                        label = data.filter.label,
                        series = data.series,
                        isExpanded = isExpanded,
                        onExpandedChange = onExpandedChange,
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
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    if (books.isEmpty()) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        HomeSectionHeader(
            label = label,
            canExpand = books.size > HOME_FILTER_PREVIEW_COUNT,
            isExpanded = isExpanded,
            onExpandedChange = onExpandedChange,
        )
    }
    val visibleBooks = if (isExpanded) books else books.take(HOME_FILTER_PREVIEW_COUNT)
    items(visibleBooks, key = { it.id.value }) { book ->
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
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
) {
    if (series.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        HomeSectionHeader(
            label = label,
            canExpand = series.size > HOME_FILTER_PREVIEW_COUNT,
            isExpanded = isExpanded,
            onExpandedChange = onExpandedChange,
        )
    }

    val visibleSeries = if (isExpanded) series else series.take(HOME_FILTER_PREVIEW_COUNT)
    items(visibleSeries, key = { it.id.value }) {
        SeriesImageCard(
            series = it,
            onSeriesClick = { onSeriesClick(it) },
            seriesMenuActions = seriesMenuActions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun HomeSectionHeader(
    label: String,
    canExpand: Boolean,
    isExpanded: Boolean,
    onExpandedChange: () -> Unit,
) {
    val strings = LocalStrings.current
    val layout = LocalKomeliaLayout.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = layout.sectionSpacing - layout.gridSpacing)
            .heightIn(min = layout.minimumTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            strings.legacy.forText(label),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f),
        )
        if (canExpand) {
            TextButton(
                onClick = onExpandedChange,
                modifier = Modifier.heightIn(min = layout.minimumTouchTarget),
            ) {
                Text(
                    if (isExpanded) strings.filters.filterTagsShowLess
                    else strings.filters.filterTagsShowMore
                )
            }
        }
    }
}
