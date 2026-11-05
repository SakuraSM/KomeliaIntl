package snd.komelia.ui.series.view

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import snd.komelia.ui.common.components.KomeliaDropdownMenu
import snd.komelia.ui.common.components.KomeliaModalBottomSheet
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_page_size
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_content_type
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_close_settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_list_view
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_grid_view
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_tab_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_book_filter_authors
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_book_filter_read_status
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_book_filter_sort
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_book_filter_tags
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_book_filters
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_book_selection_mode
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_no_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_no_books_matching_filters
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.settings.model.BooksLayout
import snd.komelia.settings.model.BooksLayout.GRID
import snd.komelia.settings.model.BooksLayout.LIST
import snd.komga.client.book.KomgaBookId
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.book.BooksFilterState
import snd.komelia.ui.book.BooksFilterState.BooksSort
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.components.FilterDropdownChoice
import snd.komelia.ui.common.components.FilterDropdownMultiChoice
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.common.components.TagFiltersDropdownMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.bulk.BookBulkActions
import snd.komelia.ui.common.menus.bulk.BooksBulkActionsContent
import snd.komelia.ui.common.menus.bulk.BulkActionsContainer
import snd.komelia.ui.dialogs.AppDialog
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.EXPANDED
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.series.SeriesBooksState.BooksData
import snd.komelia.ui.series.SeriesFilterState.TagExclusionMode
import snd.komelia.ui.series.SeriesFilterState.TagInclusionMode
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaAuthor

fun LazyGridScope.SeriesBooksContent(
    series: KomeliaSeries?,
    booksLoadState: LoadState<BooksData>,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    onBooksLayoutChange: (BooksLayout) -> Unit,
    onBooksPageSizeChange: (Int) -> Unit,
    onPageChange: (Int) -> Unit,
    onBookSelect: (KomeliaBook) -> Unit,
    booksFilterState: BooksFilterState,
    bookContextMenuActions: BookMenuActions,
    isMasterPane: Boolean = false,
    masterPaneGrid: Boolean = false,
    activeBookId: KomgaBookId? = null,
    tabSelector: (@Composable () -> Unit)? = null,
) {
    if (booksLoadState is LoadState.Success<BooksData>) {
        val booksState = booksLoadState.value
        if (!isMasterPane || masterPaneGrid) item(span = { GridItemSpan(maxLineSpan) }) {
            BooksToolBar(
                series = series,
                booksLayout = if (isMasterPane && !masterPaneGrid) LIST else booksState.layout,
                showViewOptions = !isMasterPane || masterPaneGrid,
                tabSelector = tabSelector,
                onBooksLayoutChange = onBooksLayoutChange,
                booksPageSize = booksState.pageSize,
                onBooksPageSizeChange = onBooksPageSizeChange,
                selectionMode = booksState.selectionMode,
                booksFilterState = booksFilterState,
                totalBookPages = booksState.totalPages,
                currentBookPage = booksState.currentPage,
                onPageChange = onPageChange
            )
        }
        if (masterPaneGrid && series != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(top = 34.dp, bottom = 8.dp).heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(series.metadata.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
                Text(booksState.books.size.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BooksContent(
            books = booksState.books,
            booksFilterState = booksFilterState,
            onBookClick = onBookClick,
            onBookReadClick = onBookReadClick,
            bookMenuActions = bookContextMenuActions,
            selectionMode = booksState.selectionMode,
            selectedBooks = booksState.selectedBooks,
            onBookSelect = onBookSelect,
            layout = if (isMasterPane && !masterPaneGrid) LIST else booksState.layout,
            activeBookId = activeBookId,
            showVolumeTitles = masterPaneGrid
        )

        if (!booksState.selectionMode) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Pagination(
                    totalPages = booksState.totalPages,
                    currentPage = booksState.currentPage,
                    onPageChange = onPageChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    } else {
        item(span = { GridItemSpan(maxLineSpan) }) { LoadIndicator() }
    }
}

@Composable
private fun LoadIndicator() {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val animatedColor = remember { Animatable(background.copy(alpha = .0f)) }
    LaunchedEffect(Unit) {
        while (true) {
            animatedColor.animateTo(background, tween(1000, 200))
            delay(1000)
            animatedColor.animateTo(background.copy(alpha = .1f), tween(1500))
        }
    }
    Box(
        modifier = Modifier
            .padding(vertical = 30.dp)
            .height(500.dp)
            .fillMaxWidth()
            .background(animatedColor.value)
            .clip(RoundedCornerShape(10.dp))
    )

}

private fun LazyGridScope.BooksContent(
    books: List<KomeliaBook>,
    booksFilterState: BooksFilterState,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    bookMenuActions: BookMenuActions,

    selectionMode: Boolean,
    selectedBooks: List<KomeliaBook>,
    onBookSelect: (KomeliaBook) -> Unit,
    layout: BooksLayout,
    activeBookId: KomgaBookId? = null,
    showVolumeTitles: Boolean = false,
) {
    if (books.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val filter by booksFilterState.state.collectAsState()
            val hasFilters = filter.readStatus.isNotEmpty() || filter.includeTags.isNotEmpty() ||
                filter.excludeTags.isNotEmpty() || filter.authors.isNotEmpty()
            Text(
                stringResource(if (hasFilters) Res.string.series_no_books_matching_filters else Res.string.series_no_books),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else
        when (layout) {
            GRID -> {
                BooksGrid(
                    activeBookId = activeBookId, showVolumeTitles = showVolumeTitles,
                    books = books,
                    onBookClick = if (selectionMode) onBookSelect else onBookClick,
                    onBookReadClick = if (selectionMode) null else onBookReadClick,
                    bookMenuActions = if (selectionMode) null else bookMenuActions,

                    selectedBooks = selectedBooks,
                    onBookSelect = onBookSelect,
                )
            }

            LIST -> BooksList(
                activeBookId = activeBookId,
                books = books,
                onBookClick = if (selectionMode) onBookSelect else onBookClick,
                onBookReadClick = if (selectionMode) null else onBookReadClick,
                bookMenuActions = if (selectionMode) null else bookMenuActions,
                selectedBooks = selectedBooks,
                onBookSelect = onBookSelect,
            )
        }

}

@Composable
private fun BooksToolBar(
    series: KomeliaSeries?,
    booksLayout: BooksLayout,
    onBooksLayoutChange: (BooksLayout) -> Unit,
    booksPageSize: Int,
    onBooksPageSizeChange: (Int) -> Unit,
    selectionMode: Boolean,
    booksFilterState: BooksFilterState,

    totalBookPages: Int,
    currentBookPage: Int,
    onPageChange: (Int) -> Unit,
    showViewOptions: Boolean = true,
    tabSelector: (@Composable () -> Unit)? = null,
) {
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val currentFilter = booksFilterState.state.collectAsState().value
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (tabSelector != null) tabSelector()
            else Text(series?.let { stringResource(Res.string.series_tab_books) + " · " + it.booksCount } ?: "",
                style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        if (!selectionMode) Box {
            IconButton(onClick = { showSort = true }) {
                Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(Res.string.series_book_filter_sort))
            }
            KomeliaDropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                BooksSort.entries.forEach { order ->
                    DropdownMenuItem(text = { Text(stringResource(AppStrings.forBookSort(order))) },
                        onClick = { booksFilterState.onSortOrderChange(order); showSort = false })
                }
            }
        }
        if (!selectionMode) IconButton(onClick = { showFilters = true }) {
            Icon(Icons.Rounded.FilterList, stringResource(Res.string.series_book_filters),
                tint = if (booksFilterState.isChanged) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        if (showViewOptions) {
            IconButton(onClick = { onBooksLayoutChange(GRID) }, modifier = Modifier.semantics { selected = booksLayout == GRID }) {
                Icon(Icons.Rounded.GridView, stringResource(Res.string.reading_grid_view), Modifier.size(20.dp),
                    tint = if (booksLayout == GRID) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
            IconButton(onClick = { onBooksLayoutChange(LIST) }, modifier = Modifier.semantics { selected = booksLayout == LIST }) {
                Icon(Icons.AutoMirrored.Rounded.ViewList, stringResource(Res.string.reading_list_view), Modifier.size(20.dp),
                    tint = if (booksLayout == LIST) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
        }
    }
    if (showFilters) BookFilterDialog(booksFilterState, onDismiss = { showFilters = false }, extraControls = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.browse_page_size), modifier = Modifier.weight(1f))
            PageSizeSelectionDropdown(booksPageSize, onBooksPageSizeChange)
        }
    })
}

@Composable
fun BooksBulkActionsToolbar(
    onCancel: () -> Unit,
    books: List<KomeliaBook>,
    actions: BookBulkActions,
    selectedBooks: List<KomeliaBook>,
    onBookSelect: (KomeliaBook) -> Unit,
) {
    BulkActionsContainer(
        onCancel = onCancel,
        selectedCount = selectedBooks.size,
        allSelected = books.size == selectedBooks.size,
        onSelectAll = {
            if (books.size == selectedBooks.size) books.forEach { onBookSelect(it) }
            else books.filter { it !in selectedBooks }.forEach { onBookSelect(it) }
        }
    ) {
        when (LocalWindowWidth.current) {
            FULL, EXPANDED -> {
                if (selectedBooks.isEmpty()) {
                    Text(stringResource(Res.string.series_book_selection_mode))
                } else {
                    Spacer(Modifier.weight(1f))
                    BooksBulkActionsContent(
                        books = selectedBooks,
                        actions = actions,
                        compact = false
                    )
                }
            }

            COMPACT, MEDIUM -> {}
        }
    }
}

@Composable
fun ExpandableBookFiltersRow(filterState: BooksFilterState) {
    var showFilters by remember { mutableStateOf(false) }
    val currentFilter = filterState.state.collectAsState().value
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            val widthModifier = Modifier.width(200.dp)

            SortOrder(
                sortOrder = currentFilter.sortOrder,
                filterState = filterState,
                modifier = widthModifier,
                withLabel = false
            )
            ReadStatusFilter(
                readStatus = currentFilter.readStatus,
                filterState = filterState,
                modifier = widthModifier,
                withLabel = false
            )

            AnimatedVisibility(showFilters && filterState.authorsOptions.isNotEmpty()) {
                AuthorsFilter(
                    authors = currentFilter.authors,
                    filterState = filterState,
                    modifier = widthModifier,
                    withLabel = false
                )
            }

            AnimatedVisibility(showFilters && filterState.tagOptions.isNotEmpty()) {
                TagsFilter(
                    includeTags = currentFilter.includeTags,
                    excludeTags = currentFilter.excludeTags,
                    inclusionMode = currentFilter.inclusionMode,
                    exclusionMode = currentFilter.exclusionMode,
                    filterState = filterState,
                    modifier = widthModifier,
                    withLabel = false
                )
            }
        }

        if (filterState.authorsOptions.isNotEmpty() || filterState.tagOptions.isNotEmpty()) {
            IconButton(onClick = { showFilters = !showFilters }, modifier = Modifier.cursorForHand()) {
                Icon(
                    imageVector = if (showFilters) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookFilterDialog(
    filterState: BooksFilterState,
    onDismiss: () -> Unit,
    extraControls: @Composable () -> Unit = {},
) {
    val currentFilter = filterState.state.collectAsState().value
    val controls: @Composable () -> Unit = {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SortOrder(
                    sortOrder = currentFilter.sortOrder,
                    filterState = filterState,
                    modifier = Modifier.fillMaxWidth(),
                    withLabel = true
                )
                ReadStatusFilter(
                    readStatus = currentFilter.readStatus,
                    filterState = filterState,
                    modifier = Modifier.fillMaxWidth(),
                    withLabel = true
                )

                if (filterState.authorsOptions.isNotEmpty())
                    AuthorsFilter(
                        authors = currentFilter.authors,
                        filterState = filterState,
                        modifier = Modifier.fillMaxWidth(),
                        withLabel = true
                    )

                if (filterState.tagOptions.isNotEmpty())
                    TagsFilter(
                        includeTags = currentFilter.includeTags,
                        excludeTags = currentFilter.excludeTags,
                        inclusionMode = currentFilter.inclusionMode,
                        exclusionMode = currentFilter.exclusionMode,
                        filterState = filterState,
                        modifier = Modifier.fillMaxWidth(),
                        withLabel = true
                    )

                extraControls()
            }
        }
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.series_book_filters), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(Res.string.navigation_close_settings)) }
        }
    }
    if (LocalWindowWidth.current == COMPACT || LocalWindowWidth.current == MEDIUM) {
        KomeliaModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                header()
                controls()
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.navigation_close_settings))
                }
            }
        }
    } else AppDialog(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        content = controls, header = header, onDismissRequest = onDismiss)

}

@Composable
private fun SortOrder(
    sortOrder: BooksSort,
    filterState: BooksFilterState,
    modifier: Modifier,
    withLabel: Boolean,
) {
    FilterDropdownChoice(
        selectedOption = LabeledEntry(sortOrder, stringResource(AppStrings.forBookSort(sortOrder))),
        options = BooksSort.entries.map { LabeledEntry(it, stringResource(AppStrings.forBookSort(it))) },
        onOptionChange = { filterState.onSortOrderChange(it.value) },
        label = if (withLabel) stringResource(Res.string.series_book_filter_sort) else null,
        modifier = modifier
    )
}

@Composable
private fun ReadStatusFilter(
    readStatus: List<KomgaReadStatus>,
    filterState: BooksFilterState,
    modifier: Modifier,
    withLabel: Boolean,
) {
    FilterDropdownMultiChoice(
        selectedOptions = readStatus.map { LabeledEntry(it, stringResource(AppStrings.forReadStatus(it))) },
        options = KomgaReadStatus.entries.map { LabeledEntry(it, stringResource(AppStrings.forReadStatus(it))) },
        onOptionSelect = { changed -> filterState.onReadStatusSelect(changed.value) },
        label = if (withLabel) stringResource(Res.string.series_book_filter_read_status) else null,
        placeholder = if (withLabel) null else stringResource(Res.string.series_book_filter_read_status),
        modifier = modifier
    )
}

@Composable
private fun AuthorsFilter(
    authors: List<KomgaAuthor>,
    filterState: BooksFilterState,
    modifier: Modifier,
    withLabel: Boolean,
) {
    FilterDropdownMultiChoice(
        selectedOptions = authors.map { LabeledEntry(it, it.name) },
        options = filterState.authorsOptions.map { LabeledEntry(it, it.name) },
        onOptionSelect = { changed -> filterState.onAuthorSelect(changed.value) },
        label = if (withLabel) stringResource(Res.string.series_book_filter_authors) else null,
        placeholder = if (withLabel) null else stringResource(Res.string.series_book_filter_authors),
        modifier = modifier
    )
}

@Composable
private fun TagsFilter(
    includeTags: List<String>,
    excludeTags: List<String>,
    inclusionMode: TagInclusionMode,
    exclusionMode: TagExclusionMode,
    filterState: BooksFilterState,
    modifier: Modifier,
    withLabel: Boolean,
) {
    TagFiltersDropdownMenu(
        allTags = filterState.tagOptions,
        includeTags = includeTags,
        excludeTags = excludeTags,
        onTagSelect = filterState::onTagSelect,
        onReset = filterState::resetTagFilters,

        inclusionMode = inclusionMode,
        onInclusionModeChange = filterState::onInclusionModeChange,
        exclusionMode = exclusionMode,
        onExclusionModeChange = filterState::onExclusionModeChange,

        label = if (withLabel) stringResource(Res.string.series_book_filter_tags) else null,
        placeholder = if (withLabel) null else stringResource(Res.string.series_book_filter_tags),
        contentPadding = PaddingValues(5.dp),
        modifier = modifier.clip(RoundedCornerShape(5.dp)),
        inputFieldColor = MaterialTheme.colorScheme.surfaceVariant,
        inputFieldModifier = Modifier.fillMaxWidth()
    )
}

private fun LazyGridScope.BooksGrid(
    books: List<KomeliaBook>,
    activeBookId: KomgaBookId? = null,
    showVolumeTitles: Boolean = false,
    onBookClick: ((KomeliaBook) -> Unit)? = null,
    onBookReadClick: ((KomeliaBook, Boolean) -> Unit)? = null,
    bookMenuActions: BookMenuActions? = null,

    selectedBooks: List<KomeliaBook> = emptyList(),
    onBookSelect: ((KomeliaBook) -> Unit)? = null,
) {
    items(books) { book ->
        Column {
            BookImageCard(
                book = book,
                isActive = activeBookId == book.id, showVolumeTitle = showVolumeTitles,
                onBookClick = onBookClick?.let { { onBookClick(book) } },
                onBookReadClick = onBookReadClick?.let { { onBookReadClick(book, it) } },
                bookMenuActions = bookMenuActions,
                isSelected = selectedBooks.any { it.id == book.id },
                onSelect = onBookSelect?.let { { onBookSelect(book) } },
            )
            if (!showVolumeTitles) Spacer(Modifier.height(15.dp))
        }

    }
}

fun LazyGridScope.BooksList(
    books: List<KomeliaBook>,
    activeBookId: KomgaBookId? = null,
    bookMenuActions: BookMenuActions? = null,
    onBookClick: ((KomeliaBook) -> Unit)? = null,
    onBookReadClick: ((KomeliaBook, Boolean) -> Unit)? = null,

    selectedBooks: List<KomeliaBook> = emptyList(),
    onBookSelect: ((KomeliaBook) -> Unit)? = null,
) {
    books.forEach { book ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                BookDetailedListCard(
                    book = book,
                    compact = true,
                    isActive = activeBookId == book.id,
                    compactCoverSlotWidth = if (activeBookId != null) 64.dp else 80.dp,
                    showTrailingAction = activeBookId == null,
                    onClick = onBookClick?.let { { onBookClick(book) } },
                    onBookReadClick = onBookReadClick?.let { { onBookReadClick(book, it) } },
                    bookMenuActions = bookMenuActions,
                    isSelected = selectedBooks.any { it.id == book.id },
                    onSelect = onBookSelect?.let { { onBookSelect(book) } },
                )
            }
        }
    }
}
