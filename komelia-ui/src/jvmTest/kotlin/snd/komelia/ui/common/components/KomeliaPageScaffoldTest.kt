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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle

class KomeliaPageScaffoldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedHeadersConsumeTheCutoutInsetOnlyOnce() {
        compose.setContent {
            CompositionLocalProvider(LocalPageTopInset provides 32.dp) {
                KomeliaSafeTopBar {
                    KomeliaSafeTopBar {
                        Box(Modifier.size(48.dp).testTag("nested-control"))
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(32.dp, compose.onNodeWithTag("nested-control").getUnclippedBoundsInRoot().top)
    }

    @Test fun backgroundReachesTheTopWhileHeaderControlsAvoidTheCutout() {
        compose.setContent {
            CompositionLocalProvider(
                LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS, renderingSupported = false),
                LocalPageTopInset provides 32.dp,
            ) {
                Box(Modifier.size(360.dp, 600.dp)) {
                    KomeliaPageScaffold(topBar = { Box(Modifier.fillMaxWidth().height(48.dp).testTag("safe-header")) }) { inset ->
                        Box(Modifier.size(360.dp, 600.dp).testTag("edge-background")) {
                            Text("Content", Modifier.padding(top = inset).testTag("safe-content"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(0.dp, compose.onNodeWithTag("edge-background").getUnclippedBoundsInRoot().top)
        assertEquals(32.dp, compose.onNodeWithTag("safe-header").getUnclippedBoundsInRoot().top)
        assertEquals(80.dp, compose.onNodeWithTag("safe-content").getUnclippedBoundsInRoot().top)
    }

    @Test fun readingActionsStayAtTheViewportBottomWhenContentScrolls() {
        compose.setContent {
            Box(Modifier.size(360.dp, 600.dp)) {
                KomeliaPageScaffold(
                    topBar = {},
                    bottomBar = { Box(Modifier.fillMaxWidth().height(72.dp).testTag("reading-actions")) },
                ) { inset ->
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = inset)) {
                        Box(Modifier.fillMaxWidth().height(1200.dp).testTag("long-content"))
                        Text("Last detail", Modifier.testTag("last-detail"))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("last-detail").performScrollTo()
        val actions = compose.onNodeWithTag("reading-actions").getUnclippedBoundsInRoot()
        assertEquals(600.dp, actions.bottom)
        assertEquals(72.dp, actions.bottom - actions.top)
        val content = compose.onNodeWithTag("long-content").getUnclippedBoundsInRoot()
        assertEquals(1200.dp, content.bottom - content.top)
    }

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
                CompositionLocalProvider(
                    LocalSurfaceAppearance provides appearance.value,
                    LocalPageTopInset provides 32.dp,
                ) {
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
