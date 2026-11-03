package snd.komelia.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.settings.model.AppTheme

class ThemeTest {
    @Test
    fun lightThemeUsesAccessibleContentPalette() {
        val colors = Theme.LIGHT.colorScheme

        assertEquals(Color(0xFFF4F7FB), colors.background)
        assertEquals(Color(0xFF13283E), colors.onBackground)
        assertEquals(Color(0xFF00638D), colors.primary)
    }

    @Test
    fun darkThemeUsesNeutralReadingSurfaces() {
        val colors = Theme.DARK.colorScheme

        assertEquals(Color(0xFF09121E), colors.background)
        assertEquals(Color(0xFF142538), colors.surface)
        assertEquals(Color(0xFF70D9FF), colors.primary)
    }

    @Test
    fun oledThemeKeepsTrueBlackBackground() {
        assertEquals(Color.Black, Theme.DARKER.colorScheme.background)
        assertEquals(Color.Black, Theme.DARKER.colorScheme.surface)
        assertEquals(Color.Black, Theme.DARKER.colorScheme.surfaceDim)
    }

    @Test
    fun persistedThemeNamesStillMapWithoutMigration() {
        with(Theme.Companion) {
            assertEquals(Theme.DARK, AppTheme.DARK.toTheme())
            assertEquals(Theme.LIGHT, AppTheme.LIGHT.toTheme())
            assertEquals(Theme.DARKER, AppTheme.DARKER.toTheme())
        }
    }

    @Test
    fun reducedMotionMakesTransitionsImmediate() {
        val motion = KomeliaMotionSpec(
            isReducedMotion = true,
            pressDurationMillis = 150,
            stateDurationMillis = 180,
            contentDurationMillis = 200,
            containerDurationMillis = 200,
            standardEasing = LinearEasing,
        )

        assertEquals(0, motion.duration(motion.contentDurationMillis))
    }
}
