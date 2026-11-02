package snd.komelia.ui.local

import snd.komelia.ui.common.components.BrowseSearchHeader
import snd.komelia.ui.common.components.BrowseOptionsButton
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_source
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_sort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_offline_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_local_summary
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_local_library
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
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.home.LocalHomeBookSort

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
    downloadsState: snd.komelia.ui.settings.offline.downloads.OfflineDownloadsState? = null,
    onManageLocalFolders: () -> Unit = {},
    onManageDownloads: () -> Unit = {},
) {
    val layout = LocalKomeliaLayout.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    PauseGlassWhileBusy(listState.isScrollInProgress)
    KomeliaPageScaffold(topBar = {
        BrowseSearchHeader(
            title = stringResource(when (selectedSource) {
                AvailableBookSource.ALL -> Res.string.local_content_title
                AvailableBookSource.LOCAL -> Res.string.home_local_books
                AvailableBookSource.DOWNLOADED -> Res.string.local_content_downloaded
            }),
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(Res.string.local_content_search),
        ) {
            BrowseOptionsButton(active = selectedSource != AvailableBookSource.ALL || selectedSort != LocalHomeBookSort.RECENTLY_ADDED) {
                LocalViewOptions(
                    selectedSource = selectedSource,
                    onSourceChange = {
                        onSourceChange(it)
                        scope.launch { listState.scrollToItem(0) }
                    },
                    selectedSort = selectedSort,
                    onSortChange = onSortChange,
                )
            }
        }
    }) { topInset ->
        Column(Modifier.fillMaxSize().padding(top = topInset)) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 12.dp, bottom = layout.gridBottomPadding),
                modifier = Modifier.fillMaxSize().padding(horizontal = layout.pageHorizontalPadding),
            ) {
                item {
                    Surface(onClick = onManageLocalFolders, color = androidx.compose.ui.graphics.Color.Transparent) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Folder, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(Res.string.settings_navigation_local_library), style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(Res.string.reading_local_summary),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(Res.string.reading_offline_books), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 12.dp))
                    if (!loading && books.isEmpty()) LocalEmptyContent(query.isNotBlank(), selectedSource)
                }
                items(books.size, key = { books[it].id.value }) { index ->
                    val book = books[index]
                    snd.komelia.ui.common.cards.BookDetailedListCard(book, onClick = { onBookClick(book) },
                        bookMenuActions = bookMenuActions, onBookReadClick = { onBookReadClick(book, it) })
                }
                if (downloadsState != null) item { LocalDownloadTasks(downloadsState, onManageDownloads) }
                if (totalPages > 1) {
                    item {
                        Pagination(totalPages, currentPage, onPageChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalViewOptions(
    selectedSource: AvailableBookSource,
    onSourceChange: (AvailableBookSource) -> Unit,
    selectedSort: LocalHomeBookSort,
    onSortChange: (LocalHomeBookSort) -> Unit,
) {
    val sources = listOf(
        LabeledEntry(AvailableBookSource.ALL, stringResource(Res.string.local_content_all)),
        LabeledEntry(AvailableBookSource.LOCAL, stringResource(Res.string.home_local_books)),
        LabeledEntry(AvailableBookSource.DOWNLOADED, stringResource(Res.string.local_content_downloaded)),
    )
    val sorts = listOf(
        LabeledEntry(LocalHomeBookSort.RECENTLY_ADDED, stringResource(Res.string.home_local_sort_recently_added)),
        LabeledEntry(LocalHomeBookSort.FILE_MODIFIED, stringResource(Res.string.home_local_sort_file_modified)),
        LabeledEntry(LocalHomeBookSort.TITLE, stringResource(Res.string.home_local_sort_title)),
    )
    DropdownChoiceMenu(
        selectedOption = sources.first { it.value == selectedSource },
        options = sources,
        onOptionChange = { onSourceChange(it.value) },
        label = { Text(stringResource(Res.string.browse_source)) },
    )
    DropdownChoiceMenu(
        selectedOption = sorts.first { it.value == selectedSort },
        options = sorts,
        onOptionChange = { onSortChange(it.value) },
        label = { Text(stringResource(Res.string.home_filter_sort)) },
    )
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
