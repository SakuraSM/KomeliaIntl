package snd.komelia.image

import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.DrawScope
import snd.komelia.image.TilingReaderImage.ReaderImageTile

// Draw the preview only through gaps. Drawing it underneath translucent high-resolution
// pixels would composite the same image twice and change alpha/color.
internal fun DrawScope.withTileFallbackClip(
    tile: ReaderImageTile,
    tiles: List<ReaderImageTile>,
    draw: DrawScope.() -> Unit,
) {
    if (!tile.isFallback) {
        draw()
        return
    }
    val canvas = drawContext.canvas
    canvas.save()
    try {
        tiles.filter { !it.isFallback && it.isVisible && it.renderImage != null }.forEach {
            canvas.clipRect(it.displayRegion, ClipOp.Difference)
        }
        draw()
    } finally {
        canvas.restore()
    }
}
