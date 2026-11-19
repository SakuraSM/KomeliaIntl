package snd.komelia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.platform.PlatformType
import kotlin.test.assertEquals

class MobileDockSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactDockKeepsItsTargetsFixedDuringRapidSelection() {
        var selected by mutableStateOf(AppDestination.HOME)
        compose.setContent {
            KomeliaTheme(AppTheme.DARKER) {
                Box(Modifier.width(360.dp)) {
                    AppBottomBar(selected, PlatformType.MOBILE) { destination, _ -> selected = destination }
                }
            }
        }

        val home = compose.onNodeWithTag("primary-tab-home")
        val search = compose.onNodeWithTag("primary-tab-search")
        val local = compose.onNodeWithTag("primary-tab-local")
        home.assertIsSelected().assertWidthIsAtLeast(48.dp)
        val homeBounds = home.fetchSemanticsNode().boundsInRoot
        val localBounds = local.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        search.performClick()
        compose.mainClock.advanceTimeByFrame()
        local.performClick()
        compose.mainClock.advanceTimeByFrame()
        local.assertIsSelected().assertWidthIsAtLeast(48.dp)
        home.assertIsNotSelected()
        search.assertIsNotSelected()
        assertEquals(homeBounds, home.fetchSemanticsNode().boundsInRoot)
        assertEquals(localBounds, local.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(200)
        compose.onNode(
            hasTestTag("navigation-selection-indicator") and hasAnyAncestor(isSelected()),
            useUnmergedTree = true,
        ).assertExists()
    }
}
