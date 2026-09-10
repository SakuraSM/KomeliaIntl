package snd.komelia.ui.reader.image.panels

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import snd.komelia.image.ImageRect
import snd.komelia.image.ImageViewport
import snd.komelia.ui.reader.image.ScreenScaleState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PanelViewportGeometry(val imageSize: IntSize, val areaSize: IntSize, val targetSize: IntSize, val panel: ImageRect)

internal fun panelOffsetAndZoom(geometry: PanelViewportGeometry): Pair<Offset, Float> {
    val (imageSize, areaSize, targetSize, panel) = geometry
    val xScale = targetSize.width.toFloat() / imageSize.width
    val yScale = targetSize.height.toFloat() / imageSize.height
    val left = panel.left.coerceAtLeast(0) * xScale
    val top = panel.top.coerceAtLeast(0) * yScale
    val right = panel.right.coerceAtMost(imageSize.width) * xScale
    val bottom = panel.bottom.coerceAtMost(imageSize.height) * yScale
    val width = (right - left).coerceAtLeast(1f)
    val height = (bottom - top).coerceAtLeast(1f)
    val scale = min(areaSize.width / width, areaSize.height / height)
    val fit = max(areaSize.width.toFloat() / targetSize.width, areaSize.height.toFloat() / targetSize.height)
    val offset = Offset((targetSize.width / 2f - left - width / 2f) * scale,
        (targetSize.height / 2f - top - height / 2f) * scale)
    return offset to scale / fit
}

internal suspend fun PanelsReaderState.PanelsPage.scaleForPanel(
    areaSize: IntSize,
    panelIndex: Int?,
    stretch: Boolean,
): ScreenScaleState {
    val scale = ScreenScaleState()
    scale.setAreaSize(areaSize)
    val image = imageResult?.image ?: return scale
    val size = image.calculateSizeForArea(areaSize, stretch) ?: return scale
    scale.setTargetSize(size.toSize())
    scale.enableOverscrollArea(true)
    val panel = panelIndex?.let { panelData?.panels?.getOrNull(it) }
    if (panel != null && panelData != null && areaSize.width > 0 && areaSize.height > 0) {
        val (offset, zoom) = panelOffsetAndZoom(PanelViewportGeometry(panelData.originalImageSize, areaSize, size, panel))
        scale.setZoom(zoom)
        scale.setOffset(offset)
    } else {
        scale.setZoom(0f)
        scale.setOffset(Offset.Zero)
    }
    return scale
}

internal suspend fun PanelsReaderState.PanelsPage.viewport(scale: ScreenScaleState, stretch: Boolean): ImageViewport? {
    val image = imageResult?.image ?: return null
    val area = scale.areaSize.value
    if (area.width <= 0 || area.height <= 0) return null
    val display = image.calculateSizeForArea(area, stretch) ?: return null
    scale.setTargetSize(display.toSize())
    val transform = scale.transformation.value
    val zoom = transform.scale
    if (!zoom.isFinite() || zoom <= 0f) return null
    val top = (((display.height * zoom - area.height) / 2 - transform.offset.y) / zoom).roundToInt().coerceIn(0, display.height)
    val left = (((display.width * zoom - area.width) / 2 - transform.offset.x) / zoom).roundToInt().coerceIn(0, display.width)
    val visible = IntRect(left, top, (left + area.width / zoom).roundToInt().coerceAtMost(display.width),
        (top + area.height / zoom).roundToInt().coerceAtMost(display.height))
    return ImageViewport(area, zoom, visible)
}

internal data class UpcomingPanelView(val page: Int, val panel: Int?)

/** null represents the existing whole-page zoom-out step after the last detected panel. */
internal fun upcomingPanelViews(
    index: PanelsReaderState.PageIndex,
    pages: Map<Int, PanelsReaderState.PanelData?>,
    count: Int,
): List<UpcomingPanelView> {
    val result = mutableListOf<UpcomingPanelView>()
    var page = index.page
    var panel = index.panel
    var zoomedOut = index.isLastPanelZoomOutActive
    while (result.size < count.coerceIn(0, 2)) {
        if (!pages.containsKey(page)) break
        val metadata = pages[page]
        if (metadata != null && panel + 1 < metadata.panels.size && !zoomedOut) {
            panel++
            result += UpcomingPanelView(page, panel)
        } else if (metadata != null && metadata.panels.isNotEmpty() && !metadata.panelCoversMajorityOfImage && !zoomedOut) {
            zoomedOut = true
            result += UpcomingPanelView(page, null)
        } else {
            page++
            if (!pages.containsKey(page)) break
            panel = 0
            zoomedOut = false
            result += UpcomingPanelView(page, pages[page]?.panels?.takeIf { it.isNotEmpty() }?.let { 0 })
        }
    }
    return result
}

internal fun panelPreloadRange(index: Int, pages: Int, count: Int): IntRange =
    if (count == 0) index..index else (index - 1).coerceAtLeast(0)..(index + count.coerceIn(1, 2)).coerceAtMost(pages - 1)
