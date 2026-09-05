package snd.komelia.ui.reader.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKeyEvents
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.reader.image.common.ScalableContainer

class ScalableContainerGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun widePinchCanShrinkFromMaximumZoom() = shrinkFromMaximum(endRadius = 30f)

    @Test fun shortPinchCanShrinkFromMaximumZoom() = shrinkFromMaximum(endRadius = 80f)

    private fun shrinkFromMaximum(endRadius: Float) {
        val scale = ScreenScaleState().apply {
            setAreaSize(IntSize(400, 600))
            setTargetSize(Size(400f, 600f))
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalPlatform provides PlatformType.MOBILE,
                LocalKeyEvents provides MutableSharedFlow(),
            ) {
                Box(Modifier.size(400.dp, 600.dp).testTag("viewport")) {
                    ScalableContainer(scale) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.runOnIdle { scale.setZoom(5f) }
        compose.onNodeWithTag("viewport").performTouchInput {
            pinch(
                start0 = Offset(center.x - 150f, center.y),
                end0 = Offset(center.x - endRadius, center.y),
                start1 = Offset(center.x + 150f, center.y),
                end1 = Offset(center.x + endRadius, center.y),
                durationMillis = 600,
            )
        }
        compose.runOnIdle {
            assertTrue("Pinch must reduce maximum zoom; actual=${scale.zoom.value}", scale.zoom.value < 4.5f)
        }
    }
}
