package snd.komelia.ui.series

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LoadState
import snd.komelia.ui.home.homeReadingBook
import snd.komelia.ui.series.view.SeriesReadingHeader
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SeriesReadingHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theReadingActionStaysBelowTheSeriesCoverAndTitle() {
        val book = homeReadingBook("resume")
        var selected: Any? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    SeriesReadingHeader(LoadState.Success(book), { selected = it }, {}) {
                        Box(Modifier.fillMaxWidth().height(114.dp).testTag("series-header"))
                    }
                }
            }
        }
        val header = compose.onNodeWithTag("series-header").getUnclippedBoundsInRoot()
        val action = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(action.top >= header.bottom, "The resume action must not cover series metadata")
        compose.onNode(hasClickAction()).performClick()
        assertSame(book, selected)
    }
}
