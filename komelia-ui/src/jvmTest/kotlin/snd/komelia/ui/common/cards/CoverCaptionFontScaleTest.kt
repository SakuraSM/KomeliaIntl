package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.Box
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
