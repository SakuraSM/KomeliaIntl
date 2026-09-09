package snd.komelia.ui.common.itemlist

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.posterColumnCount

/** One policy for all poster grids, including placeholders and the settings preview. */
@Composable
internal fun posterGridCells(cardWidth: Dp): GridCells {
    val columns = posterColumnCount(LocalPlatform.current, LocalWindowWidth.current, cardWidth)
    return if (columns == null) GridCells.Adaptive(cardWidth)
    else TouchBoundedPosterCells(columns, LocalKomeliaLayout.current.minimumTouchTarget)
}

internal data class TouchBoundedPosterCells(val requestedColumns: Int, val minimumWidth: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val fittingColumns = ((availableSize + spacing) / (minimumWidth.roundToPx() + spacing).coerceAtLeast(1))
            .coerceAtLeast(1)
        val columns = requestedColumns.coerceIn(1, fittingColumns)
        val contentSize = (availableSize - spacing * (columns - 1)).coerceAtLeast(0)
        return List(columns) { index -> contentSize / columns + if (index < contentSize % columns) 1 else 0 }
    }
}
