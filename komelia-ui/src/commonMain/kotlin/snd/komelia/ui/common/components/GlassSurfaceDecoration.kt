package snd.komelia.ui.common.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.AppSurfaceStyle

/** Neutral optical edges remain visible over a plain background, without inventing scene light. */
@Composable
internal fun Modifier.glassSurfaceEdge(shape: RoundedCornerShape): Modifier {
    val appearance = LocalSurfaceAppearance.current
    if (appearance.style != AppSurfaceStyle.LIQUID_GLASS) return this
    val isLight = appearance.theme == AppTheme.LIGHT
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val edge = Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (isLight) 0.9f else 0.62f),
            0.18f to Color.White.copy(alpha = if (isLight) 0.24f else 0.17f),
            0.55f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = if (isLight) 0.42f else 0.3f),
        )
        val edgeWidth = 1.dp.toPx()
        onDrawWithContent {
            drawContent()
            drawOutline(outline, brush = edge, alpha = 0.12f, style = Stroke(edgeWidth * 8))
            drawOutline(outline, brush = edge, alpha = 0.2f, style = Stroke(edgeWidth * 4))
            drawOutline(outline, brush = edge, style = Stroke(edgeWidth))
        }
    }
}
