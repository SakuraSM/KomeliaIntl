package snd.komelia.ui.common.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SwitchWithLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun labelAndControlHaveOneAccessibleSwitchAndOneAction() {
        val selected = mutableStateOf(false)
        var changes = 0
        compose.setContent {
            MaterialTheme {
                SwitchWithLabel(selected.value, { selected.value = it; changes++ }, { Text("Reduce transparency") })
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(1)
        val control = compose.onNodeWithText("Reduce transparency")
        control.assertHeightIsAtLeast(48.dp).assertIsOff().performClick()
        control.assertIsOn()
        compose.runOnIdle { assertEquals(1, changes) }
    }
}
