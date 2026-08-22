package snd.komelia.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_books_tab
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_series_tab
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.cards.SeriesDetailedListCard
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.search.SearchViewModel.SearchResultsTab
import snd.komga.client.series.KomgaSeries

@Composable
fun SearchContent(
    query: String,
    searchType: SearchResultsTab,
    onSearchTypeChange: (SearchResultsTab) -> Unit,

    bookResults: List<KomeliaBook>,
    bookCurrentPage: Int,
    bookTotalPages: Int,
    onBookPageChange: (Int) -> Unit,
    onBookClick: (KomeliaBook) -> Unit,

    seriesResults: List<KomgaSeries>,
    seriesCurrentPage: Int,
    seriesTotalPages: Int,
    onSeriesPageChange: (Int) -> Unit,
    onSeriesClick: (KomgaSeries) -> Unit,
) {
    if (query.isNotBlank() && bookResults.isEmpty() && seriesResults.isEmpty()) {
        EmptySearchResults()
        return
    }

    Box(
        contentAlignment = Alignment.TopCenter
    ) {
        val windowWidth = LocalWindowWidth.current
        val widthModifier = when (windowWidth) {
            WindowSizeClass.COMPACT, WindowSizeClass.MEDIUM -> Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            WindowSizeClass.EXPANDED -> Modifier.fillMaxWidth(.8f)
            WindowSizeClass.FULL -> Modifier.width(1200.dp)
        }
        val scrollState = rememberLazyListState()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SearchToolBar(
                searchType = searchType,
                onSearchTypeChange = onSearchTypeChange,
                hasSeries = seriesResults.isNotEmpty(),
                hasBooks = bookResults.isNotEmpty(),
                modifier = widthModifier
            )

            LazyColumn(
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (searchType) {
                    SearchResultsTab.SERIES -> {
                        items(seriesResults) { series ->
                            SeriesDetailedListCard(
                                series = series,
                                onClick = { onSeriesClick(series) },
                                modifier = widthModifier
                            )
                        }
                        item {
                            Pagination(
                                totalPages = seriesTotalPages,
                                currentPage = seriesCurrentPage,
                                onPageChange = onSeriesPageChange
                            )
                        }
                    }

                    SearchResultsTab.BOOKS -> {
                        items(bookResults) { book ->
                            BookDetailedListCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                modifier = widthModifier
                            )
                        }
                        item {
                            Pagination(
                                totalPages = bookTotalPages,
                                currentPage = bookCurrentPage,
                                onPageChange = onBookPageChange
                            )
                        }

                    }
                }
            }
        }

        VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun EmptySearchResults() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(100.dp))
        Text(
            stringResource(Res.string.search_no_results_title),
            style = MaterialTheme.typography.titleLarge
        )
        Text(stringResource(Res.string.search_no_results_body))
    }
}

@Composable
fun SearchToolBar(
    searchType: SearchResultsTab,
    onSearchTypeChange: (SearchResultsTab) -> Unit,
    hasSeries: Boolean,
    hasBooks: Boolean,
    modifier: Modifier
) {
    if (!hasSeries && !hasBooks) return
    Surface(
        modifier = modifier.padding(vertical = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp),
        ) {
            if (hasSeries) {
                SearchTypeSegment(
                    text = stringResource(Res.string.search_series_tab),
                    selected = searchType == SearchResultsTab.SERIES,
                    onClick = { onSearchTypeChange(SearchResultsTab.SERIES) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (hasBooks) {
                SearchTypeSegment(
                    text = stringResource(Res.string.search_books_tab),
                    selected = searchType == SearchResultsTab.BOOKS,
                    onClick = { onSearchTypeChange(SearchResultsTab.BOOKS) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchTypeSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
