package snd.komelia.ui.reader.image.continuous

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import snd.komelia.settings.model.ContinuousReadingDirection

internal fun continuousFirstPageViewport(
    image: IntSize,
    viewport: IntSize,
    scrollOffset: Int,
    direction: ContinuousReadingDirection,
    trailingSpacingPixels: Int = 0,
): IntRect {
    if (image.width <= 0 || image.height <= 0 || viewport.width <= 0 || viewport.height <= 0) return IntRect.Zero
    return when (direction) {
        ContinuousReadingDirection.TOP_TO_BOTTOM -> {
            val top = scrollOffset.coerceIn(0, image.height)
            val bottom = (top.toLong() + viewport.height).coerceAtMost(image.height.toLong()).toInt()
            IntRect(0, top, image.width, bottom)
        }
        ContinuousReadingDirection.RIGHT_TO_LEFT -> {
            // The Row's trailing spacer is encountered before the image in reverseLayout.
            val end = image.width.toLong() + trailingSpacingPixels.coerceAtLeast(0) - scrollOffset.coerceAtLeast(0)
            val right = end.coerceIn(0L, image.width.toLong()).toInt()
            val left = (end - viewport.width).coerceIn(0L, right.toLong()).toInt()
            IntRect(left, 0, right, image.height)
        }
        ContinuousReadingDirection.LEFT_TO_RIGHT -> {
            val left = scrollOffset.coerceIn(0, image.width)
            val right = (left.toLong() + viewport.width).coerceAtMost(image.width.toLong()).toInt()
            IntRect(left, 0, right, image.height)
        }
    }
}
