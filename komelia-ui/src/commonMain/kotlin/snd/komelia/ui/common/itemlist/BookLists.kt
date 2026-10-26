package snd.komelia.ui.common.itemlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sh.calvin.reorderable.rememberReorderableLazyGridState
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.cards.DraggableImageCard
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.platform.VerticalScrollbar

@Composable
fun BookLazyCardGrid(
    books: List<KomeliaBook>,
    onBookClick: ((KomeliaBook) -> Unit)?,
    onBookReadClick: ((KomeliaBook, Boolean) -> Unit)?,
    bookMenuActions: BookMenuActions?,

    selectedBooks: List<KomeliaBook> = emptyList(),
    onBookSelect: ((KomeliaBook) -> Unit)? = null,

    reorderable: Boolean = false,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onReorderDragStateChange: (dragging: Boolean) -> Unit = {},

    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,

    minSize: Dp = 200.dp,
    gridState: LazyGridState = rememberLazyGridState(),
    listMode: Boolean = false,
    firstItemNumber: Int = 1,
) {
    val layout = LocalKomeliaLayout.current
    val coroutineScope = rememberCoroutineScope()
    val reorderableLazyGridState = rememberReorderableLazyGridState(
        lazyGridState = gridState,
        onMove = { from, to -> onReorder(from.index, to.index) }
    )
    LaunchedEffect(reorderableLazyGridState.isAnyItemDragging) {
        onReorderDragStateChange(reorderableLazyGridState.isAnyItemDragging)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = if (listMode) GridCells.Fixed(1) else posterGridCells(minSize),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(if (listMode) 0.dp else layout.gridSpacing),
            contentPadding = PaddingValues(bottom = layout.gridBottomPadding),
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontalPadding)
        ) {

            itemsIndexed(books, key = { _, book -> book.id.value }) { index, book ->
                val isSelected = remember(selectedBooks) { selectedBooks.any { it.id == book.id } }
                DraggableImageCard(
                    key = book.id.value,
                    dragEnabled = reorderable,
                    reorderableState = reorderableLazyGridState
                ) {
                    if (listMode) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text((firstItemNumber + index).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.widthIn(min = 28.dp))
                            BookDetailedListCard(
                                book = book,
                                onClick = onBookClick?.let { { it(book) } },
                                onBookReadClick = onBookReadClick?.let { read -> { markProgress -> read(book, markProgress) } },
                                bookMenuActions = bookMenuActions,
                                isSelected = isSelected,
                                onSelect = onBookSelect?.let { select -> { select(book) } },
                                compact = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else BookImageCard(
                        book = book,
                        onBookClick = onBookClick?.let { { onBookClick(book) } },
                        bookMenuActions = bookMenuActions,
                        onBookReadClick = onBookReadClick?.let { { onBookReadClick(book, it) } },
                        isSelected = isSelected,
                        onSelect = onBookSelect?.let { { onBookSelect(book) } },
                        modifier = Modifier.fillMaxSize(),
                    )

                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Pagination(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageChange = {
                        coroutineScope.launch {
                            onPageChange(it)
                            gridState.scrollToItem(0)
                        }
                    }
                )
            }
        }
        VerticalScrollbar(gridState, Modifier.align(Alignment.TopEnd))
    }

}
