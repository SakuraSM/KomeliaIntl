package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType
import kotlin.test.assertTrue

class CoverCaptionFontScaleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun singleAndTwoLineTitlesShareTheirTopAlignment() {
        compose.setContent {
            CompositionLocalProvider(LocalPlatform provides PlatformType.MOBILE) {
                MaterialTheme {
                    Row {
                        Box(Modifier.width(111.dp)) {
                            CoverCardCaption("Short title", CoverCaptionVariant.TitleOnly)
                        }
                        Box(Modifier.width(111.dp)) {
                            CoverCardCaption("A longer title that uses two lines", CoverCaptionVariant.TitleOnly)
                        }
                    }
                }
            }
        }
        val short = compose.onNodeWithText("Short title").getUnclippedBoundsInRoot()
        val wrapped = compose.onNodeWithText("A longer title that uses two lines").getUnclippedBoundsInRoot()
        assertTrue(kotlin.math.abs(short.top.value - wrapped.top.value) <= 2f,
            "Titles in one row must start together: short=$short, wrapped=$wrapped")
    }

    @Test fun largeFontLeavesACompleteSupportingLineBelowTheLongTitle() {
        compose.setContent {
            CompositionLocalProvider(
                LocalPlatform provides PlatformType.MOBILE,
                LocalDensity provides Density(1f, fontScale = 1.3f),
            ) {
                MaterialTheme {
                    Box(Modifier.width(100.dp)) {
                        CoverCardCaption(
                            title = "A long title that needs both available lines",
                            variant = CoverCaptionVariant.TitleWithSupporting,
                            supportingText = "Field Notes",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val supportingBounds = compose.onNodeWithText("Field Notes").getUnclippedBoundsInRoot()
        assertTrue(supportingBounds.bottom - supportingBounds.top >= 20.dp, "A 130% supporting line must not be clipped: $supportingBounds")
    }
}
