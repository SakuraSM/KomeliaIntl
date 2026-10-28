package snd.komelia.ui.common.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import snd.komelia.settings.model.AppSurfaceStyle
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.test.assertEquals

class DropdownChoiceFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedValueReturnsKeyboardFocusToItsAnchor() {
        val first = LabeledEntry(0, "Classic")
        val second = LabeledEntry(1, "Glass")
        val selected = mutableStateOf(first)
        compose.setContent { Box(Modifier.background(Color.Magenta).padding(16.dp)) { MaterialTheme { CompositionLocalProvider(
            LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS)
        ) {
            DropdownChoiceMenu(selected.value, listOf(first, second), { selected.value = it }, inputFieldModifier = Modifier.testTag("anchor"), label = { Text("Style") })
        } } } }
        compose.onNodeWithText("Classic").performClick()
        compose.onNode(hasText("Classic") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
        compose.onNodeWithText("Glass").performClick()
        compose.onNodeWithTag("anchor").assertIsFocused()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("anchor").fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Magenta, pixels[bounds.left.roundToInt(), bounds.top.roundToInt()],
            "The focused indication must keep the input's rounded corner transparent")
    }
}
