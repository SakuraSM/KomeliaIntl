package snd.komelia.ui.common.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import snd.komelia.settings.model.AppTheme

/** Shared rendering tokens; no application preference or persisted value is introduced. */
internal object KomeliaChromeStyle {
    val blurRadius = 28.dp
    const val tintOpacity = 0.6f
    val highlightLightRadius = 12.dp
    val highlightLightOffset = DpOffset(0.dp, (-2).dp)
    val navigationHighlightInset = 6.dp
    val navigationHighlightShape = RoundedCornerShape(20.dp)
}

internal fun glassTintColor(theme: AppTheme, colors: ColorScheme): Color =
    if (theme == AppTheme.LIGHT) colors.surface else Color.Black

internal data class ChromeHighlightShadows(val shadowOpacity: Float, val lightOpacity: Float)

internal fun chromeHighlightShadows(theme: AppTheme): ChromeHighlightShadows =
    if (theme == AppTheme.LIGHT) ChromeHighlightShadows(shadowOpacity = 0f, lightOpacity = .48f)
    else ChromeHighlightShadows(shadowOpacity = 0f, lightOpacity = .065f)

internal fun chromeContentColors(theme: AppTheme, colors: ColorScheme): ColorScheme {
    val accent = if (theme == AppTheme.LIGHT) Color(0xFF00243A) else Color(0xFFEAF7FF)
    val error = if (theme == AppTheme.LIGHT) Color(0xFF4A0000) else Color(0xFFFFF4F1)
    return colors.copy(
        primary = accent,
        secondary = colors.onSurface,
        tertiary = accent,
        onSurfaceVariant = colors.onSurface,
        error = error,
    )
}

/** Provides readable content colors for a chrome surface or a modifier-backed chrome layout. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KomeliaChromeContent(content: @Composable () -> Unit) {
    val appearance = LocalSurfaceAppearance.current
    val colors = MaterialTheme.colorScheme
    val chromeColors = remember(colors, appearance) {
        if (appearance.useGlass) chromeContentColors(appearance.theme, colors) else colors
    }
    // Keep the composition structure stable when switching Classic/Glass or transparency support.
    MaterialTheme(colorScheme = chromeColors, motionScheme = MaterialTheme.motionScheme, content = content)
}
