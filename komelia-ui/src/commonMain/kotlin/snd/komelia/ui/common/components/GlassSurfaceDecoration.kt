package snd.komelia.ui.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.LocalKomeliaMotion

/** White selection light only: no stroke, inner rim, layout, gestures, or backdrop capture. */
@Composable
fun Modifier.komeliaHighlight(
    highlighted: Boolean,
    shape: Shape = MaterialTheme.shapes.medium,
    inset: Dp = 0.dp,
    interactionSource: InteractionSource? = null,
): Modifier {
    val appearance = LocalSurfaceAppearance.current
    if (appearance.style != AppSurfaceStyle.LIQUID_GLASS) return this
    val hovered = interactionSource?.collectIsHoveredAsState()?.value == true
    val focused = interactionSource?.collectIsFocusedAsState()?.value == true
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true
    val active = highlighted || hovered || focused || pressed
    val highlightShape = remember(shape, inset) { InsetHighlightShape(shape, inset) }
    if (!appearance.useGlass) {
        return if (active) background(MaterialTheme.colorScheme.primaryContainer, highlightShape) else this
    }
    val motion = LocalKomeliaMotion.current
    val strength by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(motion.duration(motion.stateDurationMillis), easing = motion.standardEasing),
        label = "chromeHighlightShadow",
    )
    if (!active && strength == 0f) return this
    val shadows = chromeHighlightShadows(appearance.theme)
    return dropShadow(highlightShape, Shadow(
        radius = KomeliaChromeStyle.highlightLightRadius,
        color = Color.White,
        alpha = shadows.lightOpacity * strength,
        offset = KomeliaChromeStyle.highlightLightOffset,
    ))
}

private data class InsetHighlightShape(val shape: Shape, val inset: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val offset = with(density) { inset.toPx() }.coerceIn(0f, size.minDimension / 2)
        val inner = shape.createOutline(Size(size.width - offset * 2, size.height - offset * 2), layoutDirection, density)
        return Outline.Generic(Path().apply {
            addOutline(inner)
            translate(Offset(offset, offset))
        })
    }
}
