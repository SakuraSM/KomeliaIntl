package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_done
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class BrowseOptionsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longFilterContentKeepsDoneVisibleWithoutScrolling() {
        var doneLabel = ""
        compose.setContent {
            doneLabel = stringResource(Res.string.reading_done)
            CompositionLocalProvider(
                LocalPlatform provides PlatformType.MOBILE,
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
            ) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, 640.dp)) {
                        BrowseOptionsButton {
                            repeat(24) { index -> Text("Filter $index", Modifier.height(48.dp)) }
                        }
                    }
                }
            }
        }
        compose.onAllNodes(hasClickAction())[0].performClick()
        compose.onNodeWithText(doneLabel).assertIsDisplayed().performClick()
        compose.onNodeWithText("Filter 0").assertDoesNotExist()
    }
}
