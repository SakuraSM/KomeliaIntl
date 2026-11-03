package snd.komelia.ui.search

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.enum_all
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.komeliaHighlight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_books_tab
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_offline_only
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_remote_only
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_series_tab
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.common.components.LocalNavigationBarInset
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.cards.SeriesDetailedListCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.search.SearchViewModel.SearchResultsTab

@Composable
fun SearchContent(
    query: String,
    searchType: SearchResultsTab,
    coverage: SearchCoverage,
    onSearchTypeChange: (SearchResultsTab) -> Unit,

    bookResults: List<KomeliaBook>,
    bookCurrentPage: Int,
    bookTotalPages: Int,
    onBookPageChange: (Int) -> Unit,
    onBookClick: (KomeliaBook) -> Unit,

    seriesResults: List<KomeliaSeries>,
    seriesCurrentPage: Int,
    seriesTotalPages: Int,
    onSeriesPageChange: (Int) -> Unit,
    onSeriesClick: (KomeliaSeries) -> Unit,
    showToolbar: Boolean = true,
    showAll: Boolean = false,
    onShowAll: () -> Unit = {},
    seriesCoverage: SearchCoverage = coverage,
    bookCoverage: SearchCoverage = coverage,
    bookMenuActions: BookMenuActions? = null,
    seriesMenuActions: SeriesMenuActions? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val layout = LocalKomeliaLayout.current
        val widthModifier = Modifier
            .widthIn(max = layout.contentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
        val toolbarWidthModifier = Modifier.widthIn(max = layout.contentMaxWidth).fillMaxWidth().padding(horizontal = 16.dp)
        val scrollState = rememberLazyListState()
        PauseGlassWhileBusy(scrollState.isScrollInProgress)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showToolbar) SearchToolBar(
                searchType = searchType,
                onSearchTypeChange = onSearchTypeChange,
                modifier = toolbarWidthModifier,
                showAll = showAll, onShowAll = onShowAll, seriesCount = seriesResults.size, bookCount = bookResults.size,
                showCounts = seriesTotalPages <= 1 && bookTotalPages <= 1,
            )

            if (!showAll && coverage != SearchCoverage.COMPLETE) {
                SearchCoverageBanner(coverage, widthModifier)
            }

            val visibleResultsEmpty = if (showAll) seriesResults.isEmpty() && bookResults.isEmpty()
                else if (searchType == SearchResultsTab.SERIES) seriesResults.isEmpty() else bookResults.isEmpty()
            if (query.isNotBlank() && visibleResultsEmpty) {
                EmptySearchResults()
                return@Column
            }
            LazyColumn(
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = layout.pageVerticalPadding + LocalNavigationBarInset.current),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showAll || searchType == SearchResultsTab.SERIES) {
                    if (showAll && seriesCoverage != SearchCoverage.COMPLETE) item { SearchCoverageBanner(seriesCoverage, widthModifier) }
                    items(seriesResults, key = { "series-${it.id.value}" }) { series ->
                        SeriesDetailedListCard(series, onClick = { onSeriesClick(series) }, modifier = widthModifier, menu = seriesMenuActions, searchResult = true)
                    }
                    if (seriesTotalPages > 1) item { Pagination(seriesTotalPages, seriesCurrentPage, onSeriesPageChange) }
                }
                if (showAll || searchType == SearchResultsTab.BOOKS) {
                    if (showAll && bookCoverage != SearchCoverage.COMPLETE) item { SearchCoverageBanner(bookCoverage, widthModifier) }
                    items(bookResults, key = { "book-${it.id.value}" }) { book ->
                        BookDetailedListCard(book, onClick = { onBookClick(book) }, modifier = widthModifier, bookMenuActions = bookMenuActions, searchResult = true)
                    }
                    if (bookTotalPages > 1) item { Pagination(bookTotalPages, bookCurrentPage, onBookPageChange) }
                }
            }
        }

        VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun SearchCoverageBanner(coverage: SearchCoverage, modifier: Modifier) {
    val offlineOnly = coverage == SearchCoverage.OFFLINE_ONLY
    Surface(
        modifier = modifier.padding(bottom = LocalKomeliaLayout.current.controlSpacing),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = LocalKomeliaLayout.current.cardContentPadding,
                vertical = LocalKomeliaLayout.current.controlSpacing,
            ),
        ) {
            Icon(
                imageVector = if (offlineOnly) Icons.Rounded.CloudOff else Icons.Rounded.Storage,
                contentDescription = null,
            )
            Text(
                text = stringResource(
                    if (offlineOnly) Res.string.search_offline_only else Res.string.search_remote_only,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptySearchResults() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.controlSpacing),
        ) {
            Text(
                stringResource(Res.string.search_no_results_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(Res.string.search_no_results_body))
        }
    }
}

@Composable
fun SearchToolBar(
    searchType: SearchResultsTab,
    onSearchTypeChange: (SearchResultsTab) -> Unit,
    modifier: Modifier,
    showAll: Boolean = false,
    onShowAll: () -> Unit = {},
    seriesCount: Int = 0,
    bookCount: Int = 0,
    showCounts: Boolean = true,
) {
    Row(modifier.selectableGroup().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchResultTab(stringResource(Res.string.enum_all) + if (showCounts) " ${seriesCount + bookCount}" else "", showAll, Modifier.weight(1f), onShowAll, "all")
        SearchResultTab(stringResource(Res.string.search_series_tab) + if (showCounts) " $seriesCount" else "", !showAll && searchType == SearchResultsTab.SERIES,
            Modifier.weight(1f), { onSearchTypeChange(SearchResultsTab.SERIES) }, "series")
        SearchResultTab(stringResource(Res.string.search_books_tab) + if (showCounts) " $bookCount" else "", !showAll && searchType == SearchResultsTab.BOOKS,
            Modifier.weight(1f), { onSearchTypeChange(SearchResultsTab.BOOKS) }, "books")
    }
}

@Composable
private fun SearchResultTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit, id: String) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.heightIn(min = LocalKomeliaLayout.current.minimumTouchTarget).semantics {
            this.selected = selected; role = androidx.compose.ui.semantics.Role.Tab
        }) {
        Box(contentAlignment = Alignment.Center) {
            Surface(color = if (selected) colors.primary else colors.surfaceContainerLow,
                contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().height(34.dp).testTag("search-result-$id-paint")) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
