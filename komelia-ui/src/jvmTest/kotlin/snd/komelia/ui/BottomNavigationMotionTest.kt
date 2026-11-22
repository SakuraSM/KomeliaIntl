package snd.komelia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import snd.komelia.ui.platform.PlatformType
import kotlin.math.abs
import kotlin.test.assertTrue

class BottomNavigationMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun androidDockUsesTheGlassFallbackWhenTransparencyIsReduced() {
        compose.setContent {
            KomeliaTheme(AppTheme.DARKER) {
                CompositionLocalProvider(
                    LocalSurfaceAppearance provides SurfaceAppearance(
                        style = AppSurfaceStyle.LIQUID_GLASS,
                        theme = AppTheme.DARKER,
                        reduceTransparency = true,
                    ),
                ) {
                    Box(Modifier.width(390.dp)) {
                        AppBottomBar(AppDestination.HOME, PlatformType.MOBILE) { _, _ -> }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("primary-navigation").captureToImage().toPixelMap()
        val material = pixels[pixels.width / 2, 4]
        assertTrue(distance(Color(0xFF09121E), material) < .01f && material.alpha == 1f, "The dock must use the opaque glass tint")
    }

    @Test fun selectionBackgroundTransitionsWithoutDelayingSelection() {
        val selected = mutableStateOf(AppDestination.HOME)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KomeliaTheme(AppTheme.DARK) {
                Box(Modifier.width(390.dp)) { AppBottomBar(selected.value) { destination, _ -> selected.value = destination } }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val before = backgroundPixel("primary-tab-search")
        compose.runOnIdle { selected.value = AppDestination.SEARCH }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("primary-tab-search").assertIsSelected()
        val during = backgroundPixel("primary-tab-search")
        compose.mainClock.advanceTimeBy(500)
        val after = backgroundPixel("primary-tab-search")
        assertTrue(distance(before, during) > .01f, "selection background did not start changing")
        assertTrue(distance(during, after) > .01f, "selection background jumped directly to its final color")
    }

    @Test fun reducedMotionSettlesOnTheFirstFrame() {
        val selected = mutableStateOf(AppDestination.HOME)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KomeliaTheme(AppTheme.DARK, isReducedMotion = true) {
                Box(Modifier.width(390.dp)) { AppBottomBar(selected.value) { destination, _ -> selected.value = destination } }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val before = backgroundPixel("primary-tab-search")
        compose.runOnIdle { selected.value = AppDestination.SEARCH }
        compose.mainClock.advanceTimeByFrame()
        val firstFrame = backgroundPixel("primary-tab-search")
        compose.mainClock.advanceTimeBy(500)
        val settled = backgroundPixel("primary-tab-search")
        assertTrue(distance(before, settled) > .01f, "selected state was not visible")
        assertTrue(distance(firstFrame, settled) < .01f, "reduced motion continued after the first frame")
    }

    @Test fun rapidTapsKeepTheMostRecentlySelectedDestination() {
        val selected = mutableStateOf(AppDestination.HOME)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            KomeliaTheme(AppTheme.DARK) {
                Box(Modifier.width(390.dp)) { AppBottomBar(selected.value) { destination, _ -> selected.value = destination } }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("primary-tab-library").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("primary-tab-search").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("primary-tab-settings").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("primary-tab-settings").assertIsSelected()
        compose.onNodeWithTag("primary-tab-library").assertIsNotSelected()
        compose.onNodeWithTag("primary-tab-search").assertIsNotSelected()
    }

    private fun backgroundPixel(tag: String): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, 6]
    }

    private fun distance(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
}
