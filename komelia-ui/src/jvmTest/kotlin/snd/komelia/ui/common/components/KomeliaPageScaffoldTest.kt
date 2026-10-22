package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import kotlin.test.assertEquals

class KomeliaPageScaffoldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun optionalTopBarCanBeEmptyWhileHomeIsLoading() {
        compose.setContent {
            Box(Modifier.size(360.dp, 600.dp)) {
                KomeliaPageScaffold(topBar = {}) { inset ->
                    Text("Home", Modifier.padding(top = inset).testTag("home"))
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0.dp, compose.onNodeWithTag("home").getUnclippedBoundsInRoot().top)
    }

    @Test fun changingMaterialPreservesContentPositionAndScrollRange() {
        val appearance = mutableStateOf(SurfaceAppearance())
        var maxScroll = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSurfaceAppearance provides appearance.value) {
                    Box(Modifier.size(360.dp, 600.dp)) {
                        KomeliaPageScaffold(topBar = { Box(Modifier.fillMaxWidth().height(64.dp)) }) { inset ->
                            val scroll = rememberScrollState()
                            maxScroll = scroll.maxValue
                            Column(Modifier.verticalScroll(scroll).padding(top = inset + 12.dp)) {
                                Text("First item", Modifier.testTag("first"))
                                Box(Modifier.fillMaxWidth().height(1100.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val classicTop = compose.onNodeWithTag("first").getUnclippedBoundsInRoot().top
        var classicRange = 0
        compose.runOnIdle { classicRange = maxScroll; appearance.value = SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS, renderingSupported = false) }
        compose.waitForIdle()
        assertEquals(classicTop, compose.onNodeWithTag("first").getUnclippedBoundsInRoot().top)
        compose.runOnIdle { assertEquals(classicRange, maxScroll) }
    }
}
