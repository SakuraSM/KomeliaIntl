package snd.komelia.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_home
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue

class NavigationSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassSelectionHasAVisibleMarkerAndMovesWithTheSelectedDestinationInEveryTheme() {
        var theme by mutableStateOf(AppTheme.DARK)
        var selected by mutableStateOf(AppDestination.HOME)
        var homeLabel = ""
        var searchLabel = ""
        compose.setContent {
            MaterialTheme(colorScheme = Theme.valueOf(theme.name).colorScheme) {
                CompositionLocalProvider(LocalSurfaceAppearance provides SurfaceAppearance(
                    style = AppSurfaceStyle.LIQUID_GLASS, theme = theme,
                )) {
                    homeLabel = stringResource(Res.string.navbar_home)
                    searchLabel = stringResource(Res.string.navbar_search)
                    Box(Modifier.width(360.dp)) {
                        AppBottomBar(selected) { destination, _ -> selected = destination }
                    }
                }
            }
        }
        for (appearance in AppTheme.entries) {
            compose.runOnIdle { theme = appearance; selected = AppDestination.HOME }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            compose.onNodeWithText(homeLabel).assertIsSelected().assertWidthIsAtLeast(48.dp)
            assertMarkerContrast(appearance)
            compose.onNodeWithText(searchLabel).performClick().assertIsSelected()
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            assertMarkerContrast(appearance)
        }
    }

    private fun assertMarkerContrast(theme: AppTheme) {
        val active = compose.onNode(hasTestTag("navigation-selection-indicator") and hasAnyAncestor(isSelected()), useUnmergedTree = true)
        val inactive = compose.onAllNodes(hasTestTag("navigation-selection-indicator") and hasAnyAncestor(isNotSelected()), useUnmergedTree = true)[0]
        val first = active.captureToImage().toPixelMap()
        val second = inactive.captureToImage().toPixelMap()
        val base = if (theme == AppTheme.LIGHT) Theme.LIGHT.colorScheme.surface else Color.Black
        val marker = first[first.width / 2, first.height - 1].compositeOver(base)
        val backdrop = second[second.width / 2, second.height - 1].compositeOver(base)
        val contrast = (max(marker.luminance(), backdrop.luminance()) + .05f) /
            (min(marker.luminance(), backdrop.luminance()) + .05f)
        assertTrue(contrast >= 4.5f, "$theme selected marker contrast was $contrast")
    }
}
