package snd.komelia.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed

class SettingsScreenLayoutTest {
    @get:Rule val compose = createComposeRule()


    @Test fun resizingKeepsTheActiveFormAndItsLatestContent() {
        var width by mutableStateOf(1000.dp)
        var title by mutableStateOf("Initial form")
        var mounts = 0
        var disposals = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width, 700.dp)) {
                    SettingsScreenLayout(
                        navMenu = { Text("Navigation") },
                        dismissButton = {},
                        content = {
                            DisposableEffect(Unit) {
                                mounts++
                                onDispose { disposals++ }
                            }
                            Text(title)
                        },
                    )
                }
            }
        }
        compose.runOnIdle { width = 600.dp; title = "Updated form" }
        compose.onNodeWithText("Updated form").assertIsDisplayed()
        compose.runOnIdle { width = 1000.dp }
        compose.waitForIdle()
        assertEquals(1, mounts, "Changing the navigation layout must not recreate the active form")
        assertEquals(0, disposals, "Popups and focus owners must survive a responsive breakpoint")
    }

    @Test fun compactSettingsGiveTheFormTheWholeWidth() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 600.dp)) {
                    SettingsScreenLayout(
                        navMenu = { Text("Navigation") },
                        dismissButton = {},
                        content = { Box(Modifier.fillMaxSize().testTag("form")) },
                    )
                }
            }
        }
        val bounds = compose.onNodeWithTag("form").getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 350.dp)
    }
}
