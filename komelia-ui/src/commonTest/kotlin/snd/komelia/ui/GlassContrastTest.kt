package snd.komelia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.KomeliaChromeStyle
import snd.komelia.ui.common.components.chromeContentColors
import snd.komelia.ui.common.components.chromeHighlightShadows
import snd.komelia.ui.common.components.glassTintColor

class GlassContrastTest {
    @Test fun selectedChromeUsesWhiteLightWithoutAGrayShadow() {
        for (theme in AppTheme.entries) {
            val shadows = chromeHighlightShadows(theme)
            assertEquals(0f, shadows.shadowOpacity)
            assertTrue(shadows.lightOpacity > 0f)
        }
    }
    @Test fun sharedChromeKeepsTextContrastWithAndWithoutSelection() {
        for (theme in AppTheme.entries) {
            val colors = chromeContentColors(theme, Theme.valueOf(theme.name).colorScheme)
            val foregrounds = listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary,
                colors.secondary, colors.tertiary, colors.error)
            for (source in listOf(0f, 1f)) {
                val body = bodyBackdrop(theme, source)
                val shadows = chromeHighlightShadows(theme)
                val backgrounds = listOf(0f, shadows.shadowOpacity).flatMap { shadow ->
                    listOf(0f, shadows.lightOpacity).map { light ->
                        Color.White.copy(alpha = light).compositeOver(Color.Black.copy(alpha = shadow).compositeOver(body))
                    }
                }
                for (background in backgrounds) for (foreground in foregrounds) {
                    assertTrue(contrastRatio(foreground, background) >= 4.5f,
                        "$theme text contrast over $source is ${contrastRatio(foreground, background)}")
                }
            }
        }
    }

    @Test fun everyChromeSurfaceTransmitsVisibleBackdropVariation() {
        for (theme in AppTheme.entries) {
            val dark = bodyBackdrop(theme, 0f)
            val light = bodyBackdrop(theme, 1f)
            assertTrue(abs(light.red - dark.red) >= .399f, "$theme chrome hides the backdrop")
            assertTrue(abs(light.luminance() - dark.luminance()) > .12f,
                "$theme chrome flattens the backdrop tones")
        }
    }

    @Test fun chromeContentPaletteLeavesPublicationAndSolidContainerColorsAlone() {
        for (theme in AppTheme.entries) {
            val original = Theme.valueOf(theme.name).colorScheme
            val chrome = chromeContentColors(theme, original)
            assertEquals(original.background, chrome.background)
            assertEquals(original.surface, chrome.surface)
            assertEquals(original.surfaceVariant, chrome.surfaceVariant)
            assertEquals(original.primaryContainer, chrome.primaryContainer)
            assertEquals(original.onSurface, chrome.onSurface)
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (max(first, second) + .05f) / (min(first, second) + .05f)
    }

    private fun bodyBackdrop(theme: AppTheme, source: Float): Color {
        val tint = glassTintColor(theme, Theme.valueOf(theme.name).colorScheme)
        // Neutral-input model; runtime screenshots cover blur and highlight boundaries.
        fun channel(value: Float) = source * (1 - KomeliaChromeStyle.tintOpacity) + value * KomeliaChromeStyle.tintOpacity
        return Color(channel(tint.red), channel(tint.green), channel(tint.blue))
    }
}
