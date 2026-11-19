package snd.komelia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacNavigationLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sidebarUsesCompactRowsAndRetainsSelectionWhenResized() {
        val width = mutableStateOf(WindowSizeClass.FULL)
        val selected = mutableStateOf(AppDestination.HOME)
        compose.setContent {
            CompositionLocalProvider(
                LocalMacOS provides true,
                LocalPlatform provides PlatformType.DESKTOP,
                LocalWindowWidth provides width.value,
                LocalDensity provides Density(1f),
            ) {
                KomeliaTheme(AppTheme.DARK) {
                    Box(Modifier.size(1440.dp, 840.dp)) {
                        AppNavigationRail(selected.value, { destination, _ -> selected.value = destination })
                    }
                }
            }
        }
        val rail = compose.onNodeWithTag("primary-navigation-rail").getUnclippedBoundsInRoot()
        val home = compose.onNodeWithTag("primary-rail-tab-home").getUnclippedBoundsInRoot()
        assertNear(258f, (rail.right - rail.left).value)
        assertNear(32f, home.top.value - rail.top.value)
        assertNear(48f, (home.bottom - home.top).value)
        compose.onNodeWithTag("primary-navigation-brand").assertDoesNotExist()
        compose.onNodeWithTag("primary-rail-tab-local").performClick().assertIsSelected()
        assertEquals(AppDestination.LOCAL, selected.value)
        compose.runOnIdle { width.value = WindowSizeClass.EXPANDED }
        val narrow = compose.onNodeWithTag("primary-navigation-rail").getUnclippedBoundsInRoot()
        assertNear(160f, (narrow.right - narrow.left).value)
        compose.onNodeWithTag("primary-rail-tab-local").assertIsSelected()
    }

    @Test fun narrowSidebarKeepsEnglishLabelsVisibleAtLargerFontSizes() {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val fontScale = mutableFloatStateOf(1f)
            compose.setContent {
                CompositionLocalProvider(LocalMacOS provides true, LocalPlatform provides PlatformType.DESKTOP,
                    LocalWindowWidth provides WindowSizeClass.EXPANDED, LocalDensity provides Density(1f, fontScale.floatValue)) {
                    KomeliaTheme(AppTheme.DARK) {
                        Box(Modifier.size(800.dp, 540.dp)) { AppNavigationRail(AppDestination.HOME, { _, _ -> }) }
                    }
                }
            }
            for (scale in listOf(1f, 1.5f, 2f)) {
                compose.runOnIdle { fontScale.floatValue = scale }
                for (label in listOf("Libraries", "Settings")) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    compose.onNodeWithText(label, useUnmergedTree = true)
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(label.length, layouts.single().getLineEnd(0, visibleEnd = true), "$label at font scale $scale")
                }
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    private fun assertNear(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= 1f, "expected $expected dp, got $actual dp")
    }
}
