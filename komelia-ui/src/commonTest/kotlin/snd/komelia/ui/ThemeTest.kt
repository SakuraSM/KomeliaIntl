package snd.komelia.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.LinearEasing
import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.settings.model.AppTheme

class ThemeTest {
    @Test
    fun lightThemeUsesAccessibleContentPalette() {
        val colors = Theme.LIGHT.colorScheme

        assertEquals(Color(0xFFF8F8FC), colors.background)
        assertEquals(Color(0xFF1B1C20), colors.onBackground)
        assertEquals(Color(0xFF5F5D8E), colors.primary)
    }

    @Test
    fun darkThemeUsesNeutralReadingSurfaces() {
        val colors = Theme.DARK.colorScheme

        assertEquals(Color(0xFF101014), colors.background)
        assertEquals(Color(0xFF15151A), colors.surface)
        assertEquals(Color(0xFFC5C2F0), colors.primary)
    }

    @Test
    fun oledThemeKeepsTrueBlackBackground() {
        assertEquals(Color.Black, Theme.DARKER.colorScheme.background)
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
