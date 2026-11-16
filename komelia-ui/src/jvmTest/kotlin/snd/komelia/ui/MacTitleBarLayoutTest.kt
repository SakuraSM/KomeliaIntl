package snd.komelia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.platform.TitleBarLayout
import snd.komelia.ui.reader.MacReaderTitleBar
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacTitleBarLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchMovesRightWithoutCollidingWithWindowControls() {
        compose.setContent {
            Box(Modifier.width(800.dp)) {
                TitleBarLayout(applyTitleBar = { PaddingValues(start = 112.dp, end = 8.dp) },
                    onElementsPlaced = {}, centerBias = .118f) {
                    Box(Modifier.align(Alignment.Start).width(80.dp).height(40.dp).testTag("leading"))
                    Box(Modifier.align(Alignment.CenterHorizontally).width(220.dp).height(40.dp).testTag("search"))
                    Box(Modifier.align(Alignment.End).width(48.dp).height(40.dp).testTag("trailing"))
                }
            }
        }
        val leading = compose.onNodeWithTag("leading").getUnclippedBoundsInRoot()
        val search = compose.onNodeWithTag("search").getUnclippedBoundsInRoot()
        val trailing = compose.onNodeWithTag("trailing").getUnclippedBoundsInRoot()
        assertTrue(search.left > 360.dp)
        assertTrue(search.left >= leading.right)
        assertTrue(search.right <= trailing.left)
    }

    @Test fun readerTitleBarKeepsTheExitAction() {
        var exits = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(800.dp)) {
                    TitleBarLayout(applyTitleBar = { PaddingValues(start = 112.dp, end = 8.dp) }, onElementsPlaced = {}) {
                        MacReaderTitleBar("Synthetic book", onExit = { exits++ })
                    }
                }
            }
        }
        compose.onNode(hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, exits) }
    }
}
