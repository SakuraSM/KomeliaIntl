package snd.komelia.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.settings.model.AppTheme

class ThemeTest {
    @Test
    fun lightThemeUsesAccessibleContentPalette() {
        val colors = Theme.LIGHT.colorScheme

        assertEquals(Color(0xFFF7F8FC), colors.background)
        assertEquals(Color(0xFF171A21), colors.onBackground)
        assertEquals(Color(0xFF4F46E5), colors.primary)
    }

    @Test
    fun darkThemeUsesNeutralReadingSurfaces() {
        val colors = Theme.DARK.colorScheme

        assertEquals(Color(0xFF0D0F14), colors.background)
        assertEquals(Color(0xFF12151C), colors.surface)
        assertEquals(Color(0xFFA5B4FC), colors.primary)
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
}
