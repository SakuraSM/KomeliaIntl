package snd.komelia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.glassTintColor
import snd.komelia.ui.common.components.glassTone

class GlassContrastTest {
    @Test fun regularBodyTintKeepsTextContrastOverBothBackdropExtremes() {
        for (theme in AppTheme.entries) {
            val colors = Theme.valueOf(theme.name).colorScheme
            for (source in listOf(0f, 1f)) {
                val backdrop = bodyBackdrop(theme, source)
                for (text in listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary)) {
                    val first = text.luminance()
                    val second = backdrop.luminance()
                    val ratio = (max(first, second) + .05f) / (min(first, second) + .05f)
                    assertTrue(ratio >= 4.5f, "$theme text contrast is $ratio over $source")
                }
            }
        }
    }

    @Test fun glassTransmitsVisibleBackdropVariationInEveryTheme() {
        for (theme in AppTheme.entries) {
            val dark = bodyBackdrop(theme, 0f)
            val light = bodyBackdrop(theme, 1f)
            val largestChannelShift = maxOf(abs(dark.red - light.red), abs(dark.green - light.green), abs(dark.blue - light.blue))
            assertTrue(largestChannelShift >= .12f, "$theme glass hides the backdrop: $largestChannelShift")
        }
    }

    private fun bodyBackdrop(theme: AppTheme, source: Float): Color {
        val colors = Theme.valueOf(theme.name).colorScheme
        val tone = glassTone(theme)
        val tint = glassTintColor(theme, colors)
        // Neutral-input model for the padded body region; physical-device checks cover edges and refraction.
        val target = if (tone.whitePoint > 0) 1f else 0f
        val adjusted = source * (1 - abs(tone.whitePoint)) + target * abs(tone.whitePoint)
        val graded = ((adjusted - .5f) * (1 + tone.contrast) + .5f).coerceIn(0f, 1f)
        val ambient = if (theme == AppTheme.LIGHT) 1f else 1.08f
        fun channel(value: Float) = ((graded * (1 - tone.opacity) + value * tone.opacity) * ambient).coerceIn(0f, 1f)
        return Color(channel(tint.red), channel(tint.green), channel(tint.blue))
    }

}
