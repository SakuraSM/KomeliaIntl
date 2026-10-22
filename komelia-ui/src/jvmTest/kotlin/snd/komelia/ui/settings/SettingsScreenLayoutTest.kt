package snd.komelia.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class SettingsScreenLayoutTest {
    @get:Rule val compose = createComposeRule()

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
