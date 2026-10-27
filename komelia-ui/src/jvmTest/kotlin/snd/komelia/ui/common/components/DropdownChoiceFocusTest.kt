package snd.komelia.ui.common.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import snd.komelia.settings.model.AppSurfaceStyle
import org.junit.Rule
import org.junit.Test

class DropdownChoiceFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedValueReturnsKeyboardFocusToItsAnchor() {
        val first = LabeledEntry(0, "Classic")
        val second = LabeledEntry(1, "Glass")
        val selected = mutableStateOf(first)
        compose.setContent { MaterialTheme { CompositionLocalProvider(
            LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS)
        ) {
            DropdownChoiceMenu(selected.value, listOf(first, second), { selected.value = it }, inputFieldModifier = Modifier.testTag("anchor"), label = { Text("Style") })
        } } }
        compose.onNodeWithText("Classic").performClick()
        compose.onNode(hasText("Classic") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
        compose.onNodeWithText("Glass").performClick()
        compose.onNodeWithTag("anchor").assertIsFocused()
    }
}
