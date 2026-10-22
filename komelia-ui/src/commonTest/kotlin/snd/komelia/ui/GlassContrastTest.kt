package snd.komelia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.glassTone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class GlassContrastTest {
    @Test fun regularBodyTintKeepsTextContrastOverBothBackdropExtremes() {
        for (theme in AppTheme.entries) {
            val colors = Theme.valueOf(theme.name).colorScheme
            val tone = glassTone(theme)
            for (source in listOf(0f, 1f)) {
                // Haze 2.0.1 grades neutral input, mixes the tint, then applies ambient light.
                // The edge highlight is outside the padded text region and is not modelled here.
                val target = if (tone.whitePoint > 0) 1f else 0f
                val adjusted = source * (1 - abs(tone.whitePoint)) + target * abs(tone.whitePoint)
                val graded = ((adjusted - .5f) * (1 + tone.contrast) + .5f).coerceIn(0f, 1f)
                val ambient = if (theme == AppTheme.LIGHT) 1f else 1.08f
                fun channel(tint: Float) = ((graded * (1 - tone.opacity) + tint * tone.opacity) * ambient).coerceIn(0f, 1f)
                val backdrop = Color(channel(colors.surface.red), channel(colors.surface.green), channel(colors.surface.blue))
                for (text in listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary)) {
                    val first = text.luminance()
                    val second = backdrop.luminance()
                    val ratio = (max(first, second) + .05f) / (min(first, second) + .05f)
                    assertTrue(ratio >= 4.5f, "$theme text contrast is $ratio over $source")
                }
            }
        }
    }
}
