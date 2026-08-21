package snd.komelia.ui.common.itemlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.posterColumnCount
import snd.komelia.ui.common.cards.CollectionImageCard
import snd.komelia.ui.common.components.Pagination
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.collection.KomgaCollectionId

@Composable
fun CollectionLazyCardGrid(
    collections: List<KomgaCollection>,
    onCollectionClick: (KomgaCollectionId) -> Unit,
    onCollectionDelete: (KomgaCollectionId) -> Unit,
    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    minSize: Dp = 200.dp,
    scrollState: LazyGridState = rememberLazyGridState(),
) {
    val layout = LocalKomeliaLayout.current
    val fixedColumnCount = posterColumnCount(LocalPlatform.current, LocalWindowWidth.current)
    val coroutineScope = rememberCoroutineScope()
    Box {
        LazyVerticalGrid(
            columns = fixedColumnCount?.let(GridCells::Fixed) ?: GridCells.Adaptive(minSize),
            state = scrollState,
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            contentPadding = PaddingValues(bottom = layout.gridBottomPadding),
            modifier = Modifier.padding(horizontal = layout.pageHorizontalPadding)
        ) {
            item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                if (scrollState.canScrollForward || scrollState.canScrollBackward)
                    Pagination(
                        totalPages = totalPages,
                        currentPage = currentPage,
                        onPageChange = onPageChange
                    )
            }

            items(collections) {
                CollectionImageCard(
                    collection = it,
                    onCollectionClick = { onCollectionClick(it.id) },
                    onCollectionDelete = { onCollectionDelete(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            item(
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Pagination(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageChange = {
                        coroutineScope.launch {
                            onPageChange(it)
                            scrollState.scrollToItem(0)
                        }
                    }
                )
            }

        }

        VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd))
    }
}
