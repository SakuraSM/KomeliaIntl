package snd.komelia.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle

class ModalSheetLayoutTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun glassSheetKeepsItsLastRowVisibleAfterTheNativeSheetOffset() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS)) {
                    GlassBackdropScope {
                        Box(Modifier.fillMaxSize().glassBackdrop().background(Color.Blue))
                        KomeliaModalBottomSheet(
                            onDismissRequest = {},
                            sheetState = rememberBottomSheetState(initialValue = SheetValue.Expanded,
                                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
                        ) {
                            repeat(8) { index -> Text("Sheet option $index", Modifier.height(48.dp)) }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Sheet option 7").assertIsDisplayed()
    }
}
