package snd.komelia.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import snd.komelia.ui.platform.PlatformType
import kotlin.test.assertEquals

class MainDestinationSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mobileSearchUsesSolidChromeAndLeavingRestoresTheSavedMaterial() {
        val saved = SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS, theme = AppTheme.LIGHT)
        var destination by mutableStateOf(AppDestination.HOME)
        var platform by mutableStateOf(PlatformType.MOBILE)
        lateinit var effective: SurfaceAppearance
        compose.setContent {
            CompositionLocalProvider(LocalSurfaceAppearance provides saved, LocalPlatform provides platform) {
                MainDestinationSurface(destination) {
                    val current = LocalSurfaceAppearance.current
                    SideEffect { effective = current }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(saved, effective)
            destination = AppDestination.SEARCH
        }
        compose.runOnIdle {
            assertEquals(AppSurfaceStyle.CLASSIC, effective.style, "Search navigation and content must both use the solid layout")
            assertEquals(AppTheme.LIGHT, effective.theme)
            assertEquals(AppSurfaceStyle.LIQUID_GLASS, saved.style, "The fallback must not overwrite the preference")
            destination = AppDestination.SETTINGS
        }
        compose.runOnIdle {
            assertEquals(saved, effective)
            destination = AppDestination.SEARCH
            platform = PlatformType.DESKTOP
        }
        compose.runOnIdle { assertEquals(saved, effective, "Desktop keeps its selected material") }
    }
}
