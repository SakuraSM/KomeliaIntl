package snd.komelia.ui.common.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme

class ChromeContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun materialChangesRetainTheirContentComposition() {
        var appearance by mutableStateOf(SurfaceAppearance())
        var mounts = 0
        var disposals = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSurfaceAppearance provides appearance) {
                    KomeliaChromeContent {
                        DisposableEffect(Unit) {
                            mounts++
                            onDispose { disposals++ }
                        }
                        Text("Retained chrome content")
                    }
                }
            }
        }
        compose.runOnIdle { appearance = appearance.copy(style = AppSurfaceStyle.LIQUID_GLASS) }
        compose.runOnIdle { appearance = appearance.copy(theme = AppTheme.LIGHT) }
        compose.runOnIdle { appearance = appearance.copy(reduceTransparency = true) }
        compose.runOnIdle { appearance = appearance.copy(style = AppSurfaceStyle.CLASSIC) }
        compose.runOnIdle {
            assertEquals(1, mounts)
            assertEquals(0, disposals)
        }
    }
}
