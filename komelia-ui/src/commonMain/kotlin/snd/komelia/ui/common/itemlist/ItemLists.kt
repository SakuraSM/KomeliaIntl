package snd.komelia.ui.common.itemlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.posterColumnCount
import snd.komelia.ui.common.cards.ItemCard
import snd.komelia.ui.platform.HorizontalScrollbar
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.cursorForHand

@Composable
fun PlaceHolderLazyCardGrid(
    elements: Int,
    minSize: Dp = 200.dp,
    scrollState: LazyGridState = rememberLazyGridState(),
) {
    val layout = LocalKomeliaLayout.current
    val fixedColumnCount = posterColumnCount(LocalPlatform.current, LocalWindowWidth.current)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalGrid(
            columns = fixedColumnCount?.let(GridCells::Fixed) ?: GridCells.Adaptive(minSize),
            state = scrollState,
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.gridSpacing),
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth)
                .fillMaxSize()
                .padding(horizontal = layout.pageHorizontalPadding)
        ) {
            for (i in 0 until elements) {
                item { ItemCard(onClick = {}, image = {}) }
            }
        }
        VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd))
    }

}


@Composable
fun ItemCardsSlider(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollState = rememberLazyListState()
    Card {
        val layout = LocalKomeliaLayout.current
        Column(
            Modifier.padding(layout.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(layout.itemSpacing)
        ) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ).cursorForHand()
            ) {
                label()
                Icon(Icons.Rounded.ChevronRight, null)
            }
            HorizontalDivider()
            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing),
            ) {
                content()
            }
            HorizontalScrollbar(scrollState, Modifier.align(Alignment.End).height(10.dp))
        }
    }
}
