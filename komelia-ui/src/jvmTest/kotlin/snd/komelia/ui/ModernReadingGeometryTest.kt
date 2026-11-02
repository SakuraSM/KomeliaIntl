package snd.komelia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
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

    @Test fun floatingNavigationMatchesTheApprovedPhoneFrameWithinTwoPixels() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalPlatform provides PlatformType.MOBILE,
                LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS),
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT)) {
                KomeliaTheme(AppTheme.DARK) {
                    // The design reserves 24px for the runtime-owned navigation safe area.
                    Box(Modifier.size(390.dp, 844.dp).padding(bottom = 24.dp)) {
                        Box(Modifier.align(Alignment.BottomCenter)) { AppBottomBar(AppDestination.HOME) { _, _ -> } }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("primary-navigation").getUnclippedBoundsInRoot()
        near("navigation.left", 14f, bounds.left.value)
        near("navigation.width", 362f, (bounds.right - bounds.left).value)
        near("navigation.height", 70f, (bounds.bottom - bounds.top).value)
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
        compose.onNodeWithText(localLabel).performClick()
        compose.runOnIdle { width.value = WindowSizeClass.FULL }
        compose.onNodeWithText(localLabel).assertIsSelected()
        val desktop = compose.onNodeWithTag("primary-navigation-rail").getUnclippedBoundsInRoot()
        near("desktop.rail.width", 237.26f, (desktop.right - desktop.left).value)
        compose.runOnIdle { width.value = WindowSizeClass.EXPANDED }
        compose.onNodeWithText(localLabel).assertIsSelected()
    }
}
