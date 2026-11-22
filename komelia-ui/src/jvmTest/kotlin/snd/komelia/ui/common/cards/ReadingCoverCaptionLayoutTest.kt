package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.KomeliaTheme
import kotlin.test.assertEquals
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

    @Test fun compactSeriesCaptionsAlignReadAndUnreadAtLargeFontSizes() {
        val fontScale = mutableStateOf(1f)
        val title = "从零开始的异世界生活 中文长卷册标题"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                KomeliaTheme(AppTheme.DARK) {
                    Column {
                        Row(Modifier.testTag("series-captions")) {
                            Box(Modifier.width(110.dp)) {
                                ReadingCoverCaption(title, .7f, "已读 70%", compactStacked = true)
                            }
                            Box(Modifier.width(110.dp)) {
                                ReadingCoverCaption("第 02 册", null, "未读", compactStacked = true)
                            }
                        }
                        Text(
                            "已读 70%", Modifier.width(110.dp).testTag("natural-status"),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        for (scale in listOf(1f, 1.3f, 2f)) {
            compose.runOnIdle { fontScale.value = scale }
            val frame = compose.onNodeWithTag("series-captions").getUnclippedBoundsInRoot()
            val heading = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
            val read = compose.onNode(hasText("已读 70%") and hasAnyAncestor(hasTestTag("series-captions")))
                .getUnclippedBoundsInRoot()
            val unread = compose.onNodeWithText("未读").getUnclippedBoundsInRoot()
            val natural = compose.onNodeWithTag("natural-status").getUnclippedBoundsInRoot()
            assertTrue(read.top >= heading.bottom, "The long title must leave room for reading progress at $scale")
            assertTrue(read.bottom <= frame.bottom, "Both reading states must fit below the covers at $scale")
            assertEquals(read.bottom, unread.bottom)
            assertEquals(natural.bottom - natural.top, read.bottom - read.top, "A full progress label must fit at $scale")
        }
    }
}
