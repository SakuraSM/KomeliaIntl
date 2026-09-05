package snd.komelia.ui.local

import snd.komelia.ui.search.SearchTextField

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_local_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_local_sort_file_modified
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_local_sort_recently_added
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_local_sort_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_all
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_downloaded
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_empty_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_empty_downloaded_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_empty_downloaded_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_empty_local_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_empty_local_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_empty_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_search
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_content_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.AvailableBookSource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.components.KomeliaTopBarSurface
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.home.LocalHomeBookSort
import snd.komelia.ui.posterColumnCount

@Composable
internal fun LocalContent(
    books: List<KomeliaBook>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedSource: AvailableBookSource,
    onSourceChange: (AvailableBookSource) -> Unit,
    selectedSort: LocalHomeBookSort,
    onSortChange: (LocalHomeBookSort) -> Unit,
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit,
    cardWidth: Dp,
    bookMenuActions: BookMenuActions,
    loading: Boolean,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val isContentScrolled by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }
    Column(Modifier.fillMaxSize()) {
        KomeliaTopBarSurface(isContentScrolled) {
            LocalToolbar(
                query = query,
                onQueryChange = onQueryChange,
                selectedSource = selectedSource,
                onSourceChange = {
                    onSourceChange(it)
                    scope.launch { gridState.scrollToItem(0) }
                },
                selectedSort = selectedSort,
                onSortChange = onSortChange,
            )
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (!loading && books.isEmpty()) {
            LocalEmptyContent(hasQuery = query.isNotBlank(), selectedSource = selectedSource)
            return@Column
        }
        val fixedColumnCount = posterColumnCount(LocalPlatform.current, LocalWindowWidth.current)
        LazyVerticalGrid(
            columns = fixedColumnCount?.let(GridCells::Fixed) ?: GridCells.Adaptive(cardWidth),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            contentPadding = PaddingValues(
                top = layout.topBarContentSpacing,
                bottom = layout.gridBottomPadding + layout.sectionSpacing,
            ),
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontalPadding),
        ) {
            items(books, key = { it.id.value }) { book ->
                BookImageCard(
                    book = book,
                    onBookClick = { onBookClick(book) },
                    onBookReadClick = { onBookReadClick(book, it) },
                    bookMenuActions = bookMenuActions,
                    showSeriesTitle = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (totalPages > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Pagination(totalPages, currentPage, onPageChange)
                }
            }
        }
    }
}

@Composable
private fun LocalToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedSource: AvailableBookSource,
    onSourceChange: (AvailableBookSource) -> Unit,
    selectedSort: LocalHomeBookSort,
    onSortChange: (LocalHomeBookSort) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Column(
        verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        modifier = Modifier
            .widthIn(max = layout.contentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = layout.pageHorizontalPadding, vertical = layout.controlSpacing),
    ) {
        Text(
            stringResource(Res.string.local_content_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        SearchTextField(
            query = query,
            onQueryChange = onQueryChange,
            onDone = onQueryChange,
            onDismiss = { onQueryChange("") },
            placeholder = stringResource(Res.string.local_content_search),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SourceChip(
                label = stringResource(Res.string.local_content_all),
                selected = selectedSource == AvailableBookSource.ALL,
                onClick = { onSourceChange(AvailableBookSource.ALL) },
                modifier = Modifier.weight(1f),
            )
            SourceChip(
                label = stringResource(Res.string.home_local_books),
                selected = selectedSource == AvailableBookSource.LOCAL,
                onClick = { onSourceChange(AvailableBookSource.LOCAL) },
                modifier = Modifier.weight(1f),
            )
            SourceChip(
                label = stringResource(Res.string.local_content_downloaded),
                selected = selectedSource == AvailableBookSource.DOWNLOADED,
                onClick = { onSourceChange(AvailableBookSource.DOWNLOADED) },
                modifier = Modifier.weight(1f),
            )
            LocalSortMenu(selectedSort, onSortChange)
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier.heightIn(min = LocalKomeliaLayout.current.minimumTouchTarget),
    )
}

@Composable
private fun LocalSortMenu(selected: LocalHomeBookSort, onSelect: (LocalHomeBookSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        LocalHomeBookSort.RECENTLY_ADDED to stringResource(Res.string.home_local_sort_recently_added),
        LocalHomeBookSort.FILE_MODIFIED to stringResource(Res.string.home_local_sort_file_modified),
        LocalHomeBookSort.TITLE to stringResource(Res.string.home_local_sort_title),
    )
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = LocalKomeliaLayout.current.minimumTouchTarget),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = labels.getValue(selected))
        }
        DropdownMenu(expanded, { expanded = false }) {
            LocalHomeBookSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(labels.getValue(sort)) },
                    leadingIcon = if (sort == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalEmptyContent(hasQuery: Boolean, selectedSource: AvailableBookSource) {
    val emptyTitle = when {
        hasQuery -> Res.string.search_no_results_title
        selectedSource == AvailableBookSource.LOCAL -> Res.string.local_content_empty_local_title
        selectedSource == AvailableBookSource.DOWNLOADED -> Res.string.local_content_empty_downloaded_title
        else -> Res.string.local_content_empty_title
    }
    val emptyBody = when {
        hasQuery -> Res.string.search_no_results_body
        selectedSource == AvailableBookSource.LOCAL -> Res.string.local_content_empty_local_body
        selectedSource == AvailableBookSource.DOWNLOADED -> Res.string.local_content_empty_downloaded_body
        else -> Res.string.local_content_empty_body
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.controlSpacing),
            modifier = Modifier.padding(LocalKomeliaLayout.current.pageHorizontalPadding),
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(emptyTitle),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(emptyBody),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
