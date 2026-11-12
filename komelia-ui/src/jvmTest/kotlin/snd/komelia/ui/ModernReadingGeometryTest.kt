package snd.komelia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.BrowseSearchHeader
import snd.komelia.ui.common.components.LocalPageTopInset
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import kotlin.math.abs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_local

/** Measured layout anchors on the approved phone canvas, independently of OS cutouts. */
class ModernReadingGeometryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchMatchesTheApprovedPhoneAnchorsWithinTwoPixels() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalPageTopInset provides 24.dp,
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT)) {
                KomeliaTheme(AppTheme.DARK) {
                    Box(Modifier.size(390.dp, 844.dp)) { BrowseSearchHeader("Library", "", {}) }
                }
            }
        }
        val bounds = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
        // PNG 02: 31px / 853px × 390px = 14.2px, search top 214px / 1844px × 844px = 98px.
        near("search.left", 14.2f, bounds.left.value)
        near("search.top", 98f, bounds.top.value)
        near("search.width", 362f, (bounds.right - bounds.left).value)
        near("search.height", 46.5f, (bounds.bottom - bounds.top).value)
    }

    @Test fun floatingNavigationMatchesTheApprovedPhoneFrameWithinOnePixel() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalPlatform provides PlatformType.MOBILE,
                LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS),
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT)) {
                KomeliaTheme(AppTheme.DARK) {
                    // The design reserves 24px for the runtime-owned navigation safe area.
                    Box(Modifier.requiredSize(390.dp, 844.dp).testTag("canvas").padding(bottom = 24.dp)) {
                        Box(Modifier.align(Alignment.BottomCenter)) { AppBottomBar(AppDestination.HOME) { _, _ -> } }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("primary-navigation").getUnclippedBoundsInRoot()
        val canvas = compose.onNodeWithTag("canvas").getUnclippedBoundsInRoot()
        nearOnePixel("navigation.left", 14f, (bounds.left-canvas.left).value)
        nearOnePixel("navigation.top", 747f, (bounds.top-canvas.top).value)
        nearOnePixel("navigation.width", 362f, (bounds.right - bounds.left).value)
        nearOnePixel("navigation.height", 70f, (bounds.bottom - bounds.top).value)
        listOf(AppDestination.HOME,AppDestination.LIBRARY,AppDestination.LOCAL,AppDestination.SEARCH,AppDestination.SETTINGS).forEachIndexed { index,destination ->
            val tab=compose.onNodeWithTag("primary-tab-${destination.name.lowercase()}").getUnclippedBoundsInRoot()
            nearOnePixel("navigation.$destination.x",17f+72.4f*index,(tab.left-canvas.left).value)
            nearOnePixel("navigation.$destination.width",66.4f,(tab.right-tab.left).value)
            nearOnePixel("navigation.$destination.height",70f,(tab.bottom-tab.top).value)
        }
    }

    @Test fun narrowLargeTextNavigationKeepsEvenRtlPositionsAndTouchTargets() {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.3f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalPlatform provides PlatformType.MOBILE,
                LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS),
            ) {
                KomeliaTheme(AppTheme.DARK) {
                    Box(Modifier.width(360.dp)) { AppBottomBar(AppDestination.HOME) { _, _ -> } }
                }
            }
        }
        val navigation = compose.onNodeWithTag("primary-navigation").getUnclippedBoundsInRoot()
        val width = (navigation.right - navigation.left).value
        listOf(AppDestination.HOME, AppDestination.LIBRARY, AppDestination.LOCAL, AppDestination.SEARCH, AppDestination.SETTINGS)
            .forEachIndexed { index, destination ->
                val node = compose.onNodeWithTag("primary-tab-${destination.name.lowercase()}")
                node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                val tab = node.getUnclippedBoundsInRoot()
                val center = (tab.left.value + tab.right.value) / 2
                nearOnePixel("rtl.$destination.center", navigation.right.value - width * (index + .5f) / 5, center)
            }
    }

    private fun nearOnePixel(anchor: String, expected: Float, measured: Float) {
        val deviation = abs(expected - measured)
        println("VISUAL_MEASUREMENT $anchor expected=$expected measured=$measured deviation=$deviation")
        assertTrue(deviation <= 1f, "$anchor differs by $deviation px: $measured, expected $expected")
    }

    private fun near(anchor: String, expected: Float, measured: Float) {
        val deviation = abs(expected - measured)
        println("VISUAL_MEASUREMENT $anchor expected=$expected measured=$measured deviation=$deviation")
        assertTrue(deviation <= 2f, "$anchor differs by $deviation px: $measured, expected $expected")
    }

    @Test fun resizingTheRailKeepsTheSelectedDestinationAndTheApprovedWidths() {
        val localLabel = runBlocking { getString(Res.string.navbar_local) }
        val width = mutableStateOf(WindowSizeClass.EXPANDED)
        val destination = mutableStateOf(AppDestination.LIBRARY)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowWidth provides width.value,
                LocalPlatform provides PlatformType.DESKTOP) {
                KomeliaTheme(AppTheme.DARK) {
                    Box(Modifier.size(1440.dp, 1024.dp)) {
                        AppNavigationRail(destination.value, { selected, _ -> destination.value = selected })
                    }
                }
            }
        }
        val tablet = compose.onNodeWithTag("primary-navigation-rail").getUnclippedBoundsInRoot()
        near("tablet.rail.width", 90.75f, (tablet.right - tablet.left).value)
        var library = compose.onNodeWithTag("primary-rail-tab-library").getUnclippedBoundsInRoot()
        near("tablet.library.x", 6f, library.left.value)
        near("tablet.library.y", 156f, library.top.value)
        near("tablet.library.width", 80f, (library.right-library.left).value)
        near("tablet.library.height", 84f, (library.bottom-library.top).value)
        compose.onNodeWithText(localLabel).performClick()
        compose.runOnIdle { width.value = WindowSizeClass.FULL }
        compose.onNodeWithText(localLabel).assertIsSelected()
        val desktop = compose.onNodeWithTag("primary-navigation-rail").getUnclippedBoundsInRoot()
        near("desktop.rail.width", 237.26f, (desktop.right - desktop.left).value)
        library = compose.onNodeWithTag("primary-rail-tab-library").getUnclippedBoundsInRoot()
        near("desktop.library.x", 18f, library.left.value)
        near("desktop.library.y", 170f, library.top.value)
        near("desktop.library.width", 201f, (library.right-library.left).value)
        near("desktop.library.height", 62f, (library.bottom-library.top).value)
        compose.runOnIdle { width.value = WindowSizeClass.EXPANDED }
        compose.onNodeWithText(localLabel).assertIsSelected()
    }
}
