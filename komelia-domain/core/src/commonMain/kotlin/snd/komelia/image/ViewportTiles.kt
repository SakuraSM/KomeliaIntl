package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal data class ViewportTilePlan(
    val sourceSize: IntSize,
    val sourceTileSize: Int,
    val displayScale: Double,
    val renderScale: Double,
    val visibleRegion: Rect,
)

internal data class ViewportTile(val source: IntRect, val display: Rect, val pixels: IntSize)

/** Shared geometry keeps foreground and predicted viewports on exactly the same tile grid. */
internal fun viewportTiles(plan: ViewportTilePlan): List<ViewportTile> {
    val window = tileVisibilityWindow(plan.visibleRegion)
    return buildList {
        var top = 0
        while (top < plan.sourceSize.height) {
            var left = 0
            while (left < plan.sourceSize.width) {
                val right = (left.toLong() + plan.sourceTileSize).coerceAtMost(plan.sourceSize.width.toLong()).toInt()
                val bottom = (top.toLong() + plan.sourceTileSize).coerceAtMost(plan.sourceSize.height.toLong()).toInt()
                val source = IntRect(left, top, right, bottom)
                val display = Rect((left * plan.displayScale).toFloat(), (top * plan.displayScale).toFloat(),
                    (right * plan.displayScale).toFloat(), (bottom * plan.displayScale).toFloat())
                if (window.overlaps(display)) add(ViewportTile(source, display,
                    IntSize((source.width * plan.renderScale).roundToInt().coerceAtLeast(1),
                        (source.height * plan.renderScale).roundToInt().coerceAtLeast(1))))
                left = right
            }
            top = (top.toLong() + plan.sourceTileSize).coerceAtMost(plan.sourceSize.height.toLong()).toInt()
        }
    }
}

internal fun rgbaPixelBytes(size: IntSize): Long {
    if (size.width <= 0 || size.height <= 0) return 0
    val heightBytes = size.height.toLong() * 4
    return if (size.width > Long.MAX_VALUE / heightBytes) Long.MAX_VALUE else size.width * heightBytes
}
