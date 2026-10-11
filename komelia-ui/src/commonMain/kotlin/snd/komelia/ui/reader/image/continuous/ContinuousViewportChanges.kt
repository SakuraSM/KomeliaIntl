package snd.komelia.ui.reader.image.continuous

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal fun continuousViewportChanges(state: LazyListState): Flow<Unit> =
    snapshotFlow {
        val layout = state.layoutInfo
        VisibleViewport(
            firstIndex = state.firstVisibleItemIndex,
            firstOffset = state.firstVisibleItemScrollOffset,
            size = layout.viewportSize,
            startOffset = layout.viewportStartOffset,
            endOffset = layout.viewportEndOffset,
            pages = layout.visibleItemsInfo.map { VisiblePage(it.key, it.index, it.offset, it.size) },
        )
    }.map { Unit }

private data class VisibleViewport(
    val firstIndex: Int,
    val firstOffset: Int,
    val size: IntSize,
    val startOffset: Int,
    val endOffset: Int,
    val pages: List<VisiblePage>,
)

private data class VisiblePage(val key: Any, val index: Int, val offset: Int, val size: Int)
