package snd.komelia.ui

import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.SurfaceAppearance
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceAppearanceTest {
    @Test fun classicNeverEnablesSamplingAndGlassHonorsEveryFallback() {
        for (theme in AppTheme.entries) {
            for (supported in listOf(false, true)) {
                for (reduced in listOf(false, true)) {
                    assertFalse(SurfaceAppearance(AppSurfaceStyle.CLASSIC, theme, reduced, supported).useGlass)
                    val glass = SurfaceAppearance(AppSurfaceStyle.LIQUID_GLASS, theme, reduced, supported)
                    if (supported && !reduced) assertTrue(glass.useGlass) else assertFalse(glass.useGlass)
                }
            }
        }
    }
}
