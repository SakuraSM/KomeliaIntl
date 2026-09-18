package snd.komelia.ui.reader.image.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jetbrains.compose.resources.stringResource
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_panel_prerender_off
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import kotlin.test.assertEquals

class PanelPrerenderSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionUsesActualChipsAndSupportsOffOneAndTwo() {
        val count = mutableStateOf(1)
        var offLabel = ""
        compose.setContent {
            CompositionLocalProvider(LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT)) {
                MaterialTheme {
                    offLabel = stringResource(Res.string.reader_panel_prerender_off)
                    PanelPrerenderSettings(count.value) { count.value = it }
                }
            }
        }
        compose.onNodeWithText("1").assertIsSelected()
        compose.onNodeWithText("2").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(2, count.value) }
        compose.onNodeWithText(offLabel).performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(0, count.value) }
    }
}
