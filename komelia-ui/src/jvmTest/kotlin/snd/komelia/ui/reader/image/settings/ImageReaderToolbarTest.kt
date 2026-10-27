package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_image_settings
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class ImageReaderToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsUsesTheSameUnfilledControlAsBackWithLargeTouchTargets() {
        var settingsClicks = 0
        var backLabel = ""
        var settingsLabel = ""
        compose.setContent {
            MaterialTheme {
                backLabel = stringResource(Res.string.navigation_back)
                settingsLabel = stringResource(Res.string.reader_image_settings)
                CompositionLocalProvider(LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT)) {
                    Box(Modifier.width(360.dp)) {
                        ImageReaderToolbar(book = null, onBackPress = {}, onSettingsClick = { settingsClicks++ })
                    }
                }
            }
        }
        compose.waitForIdle()
        val back = compose.onNodeWithContentDescription(backLabel)
        val settings = compose.onNodeWithContentDescription(settingsLabel)
        settings.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        back.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val backPixels = back.captureToImage().toPixelMap()
        val settingsPixels = settings.captureToImage().toPixelMap()
        assertEquals(backPixels[backPixels.width / 4, backPixels.height / 4], settingsPixels[settingsPixels.width / 4, settingsPixels.height / 4])
        settings.performClick()
        compose.runOnIdle { assertEquals(1, settingsClicks) }
    }
}
