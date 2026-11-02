package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.KomeliaTheme
import kotlin.test.assertTrue

class ReadingCoverCaptionLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longTitlesLeaveProgressVisibleAtNormalAndLargeFontSizes() {
        val fontScale = mutableStateOf(1f)
        val title = "A very long title that must not cover the reading progress"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                KomeliaTheme(AppTheme.DARK) {
                    Box(Modifier.width(104.dp).testTag("caption")) { ReadingCoverCaption(title, .86f, "Read 86%") }
                }
            }
        }
        for (scale in listOf(1f, 1.3f)) {
            compose.runOnIdle { fontScale.value = scale }
            val frame = compose.onNodeWithTag("caption").getUnclippedBoundsInRoot()
            val heading = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
            val status = compose.onNodeWithText("Read 86%").getUnclippedBoundsInRoot()
            assertTrue(status.top >= heading.bottom, "Title and reading progress must not overlap at $scale")
            assertTrue(status.bottom <= frame.bottom, "Reading progress must stay within the card at $scale")
        }
    }
}
