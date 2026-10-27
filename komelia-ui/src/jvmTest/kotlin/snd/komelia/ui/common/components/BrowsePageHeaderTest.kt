package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class BrowsePageHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun destinationHeadersShareTheirHeightWithAndWithoutActions() {
        val titles = listOf("Home", "Libraries", "Local", "Search", "Settings")
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPageTopInset provides 32.dp,
                    LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
                ) {
                    Column(Modifier.size(360.dp, 600.dp)) {
                        titles.forEachIndexed { index, title ->
                            Box(Modifier.testTag(title)) {
                                BrowsePageHeader(title, onSearchClick = if (index < 2) ({}) else null)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val heights = titles.map { title ->
            val bounds = compose.onNodeWithTag(title).getUnclippedBoundsInRoot()
            bounds.bottom - bounds.top
        }
        assertEquals(List(titles.size) { 80.dp }, heights)
    }

    @Test fun glassToolbarChoiceRetainsSelectionAndClickSemantics() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS)) {
                    ChromeFilterChip(
                        selected = true,
                        onClick = { clicks++ },
                        label = { Text("All libraries") },
                        modifier = Modifier.testTag("scope"),
                    )
                }
            }
        }
        compose.onNodeWithTag("scope").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
