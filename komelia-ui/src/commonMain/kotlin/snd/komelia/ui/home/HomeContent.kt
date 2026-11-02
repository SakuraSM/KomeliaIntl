package snd.komelia.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import snd.komelia.ui.common.components.KomeliaModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_default_recently_read_books
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.ReadingDesign
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_recent_heading
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    filters: List<HomeFilterData>,
    activeFilterNumber: Int,
    onFilterChange: (Int) -> Unit,

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
    var showRecent by remember { mutableStateOf(false) }
    PauseGlassWhileBusy(gridState.isScrollInProgress)
    KomeliaPageScaffold(topBar = {
        HomeBrowseHeader(
            filters = filters,
            recentlyReadBooks = recentlyReadBooks,
            currentFilterNumber = activeFilterNumber,
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
            onFilterChange = onFilterChange,
            onShowRecent = { showRecent = true },
        )
    }
    if (showRecent) ModalBottomSheet(onDismissRequest = { showRecent = false }) {
        Text(stringResource(Res.string.home_filter_default_recently_read_books), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = ReadingDesign.pageInset, vertical = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = ReadingDesign.pageInset)) {
            listItems(recentlyReadBooks, key = { it.id.value }) { book ->
                BookDetailedListCard(book, onClick = { showRecent = false; onBookClick(book) },
                    bookMenuActions = bookMenuActions, onBookReadClick = { showRecent = false; onBookReadClick(book, it) })
            }
        }
    }
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
    onFilterChange: (Int) -> Unit,
    onShowRecent: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val columns = snd.komelia.ui.common.itemlist.posterGridCells(cardWidth)
    val featured = remember(filters, activeFilterNumber) { homeFeaturedBook(filters, activeFilterNumber) }
    val recentLabel = stringResource(Res.string.reading_recent_heading)
    val recentFeatured = remember(recentlyReadBooks) { mostRecentlyReadingBook(recentlyReadBooks) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .padding(start = ReadingDesign.homeInset, end = 10.dp),
            state = gridState,
            columns = columns,
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            contentPadding = PaddingValues(
                top = 8.dp + topInset,
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
                    preview = true,
                    onViewAll = onShowRecent,
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
                            preview = activeFilterNumber == HOME_ALL_TAB_ID,
                            onViewAll = { onFilterChange(data.filter.order) },
                        )

                        is SeriesFilterData -> SeriesFilterEntries(
                            label = data.filter.label,
                            series = data.series,
                            onSeriesClick = onSeriesClick,
                            seriesMenuActions = seriesMenuActions,
                            preview = activeFilterNumber == HOME_ALL_TAB_ID,
                            onViewAll = { onFilterChange(data.filter.order) },
                        )

                    }
                }
            }
        }
    }
}
