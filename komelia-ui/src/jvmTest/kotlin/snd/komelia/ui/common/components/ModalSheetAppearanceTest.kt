package snd.komelia.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.Theme
import kotlin.test.assertTrue

class ModalSheetAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun pausedCaptureKeepsTheLightSheetReadableWithoutABackdropFrame() {
        compose.setContent {
            MaterialTheme(colorScheme = Theme.LIGHT.colorScheme) {
                CompositionLocalProvider(LocalSurfaceAppearance provides SurfaceAppearance(
                    style = AppSurfaceStyle.LIQUID_GLASS, theme = AppTheme.LIGHT,
                )) {
                    GlassBackdropScope {
                        PauseGlassWhileBusy(true)
                        Box(Modifier.fillMaxSize().background(Color(0xFF18344A)))
                        KomeliaModalBottomSheet(
                            onDismissRequest = {},
                            sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded,
                                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
                        ) {
                            repeat(3) { index ->
                                Text("Filter option $index", Modifier.testTag("filter-$index").fillMaxWidth().height(48.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("filter-1").captureToImage().toPixelMap()
        val background = pixels[pixels.width - 8, pixels.height / 2]
        assertTrue(background.alpha >= .98f && background.luminance() >= .7f,
            "A missing captured frame must still leave a light sheet behind its controls: $background")
    }
}
