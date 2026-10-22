package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.menus.bulk.BottomBulkActionsPanel
import snd.komelia.ui.platform.WindowSizeClass
import kotlin.test.assertTrue

class BottomBulkActionsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionsReserveSpaceAboveNavigationInsteadOfCoveringEitherRegion() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalNavigationBarInset provides 80.dp, LocalWindowWidth provides WindowSizeClass.COMPACT) {
                    Box(Modifier.size(360.dp, 600.dp)) {
                        Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).testTag("grid"))
                            BottomBulkActionsPanel {
                                TextButton(onClick = {}, modifier = Modifier.testTag("actions")) { Text("Edit") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val grid = compose.onNodeWithTag("grid").getUnclippedBoundsInRoot()
        val actions = compose.onNodeWithTag("actions").getUnclippedBoundsInRoot()
        assertTrue(grid.bottom <= actions.top, "Actions must not overlay the content: $grid / $actions")
        assertTrue(actions.bottom <= 520.dp, "The bottom 80dp belongs to navigation: $actions")
    }
}
