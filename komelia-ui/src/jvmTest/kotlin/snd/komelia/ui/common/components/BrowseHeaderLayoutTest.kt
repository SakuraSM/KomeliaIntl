package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_options
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import kotlin.test.assertEquals

class BrowseHeaderLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titlesSearchAndFiltersRemainOneRowAtLargeFontScale() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPlatform provides PlatformType.MOBILE,
                    LocalPageTopInset provides 32.dp,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f),
                    LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
                ) {
                    Column(Modifier.size(360.dp, 600.dp)) {
                        Box(Modifier.testTag("home")) { BrowsePageHeader("Home", onSearchClick = {}) }
                        Box(Modifier.testTag("library")) {
                            BrowsePageHeader("A very long library name", onSearchClick = {}) {
                                BrowseOptionsButton { Text("Library scope") }
                            }
                        }
                        Box(Modifier.testTag("search")) {
                            BrowseSearchHeader("Search", "", {}, searchVisible = true) {
                                BrowseOptionsButton { Text("Search type") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        for (name in listOf("home", "library", "search")) {
            val bounds = compose.onNodeWithTag(name).getUnclippedBoundsInRoot()
            assertEquals(80.dp, bounds.bottom - bounds.top, name)
        }
    }

    @Test fun openingInlineSearchFocusesItsFieldAndClosingPreservesTheQuery() {
        val query = mutableStateOf("")
        val searchLabel = runBlocking { getString(Res.string.navbar_search) }
        val backLabel = runBlocking { getString(Res.string.navigation_back) }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 100.dp)) {
                    BrowseSearchHeader("Local", query.value, { query.value = it })
                }
            }
        }
        compose.onNodeWithContentDescription(searchLabel).performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("sample")
        compose.onNodeWithContentDescription(backLabel).performClick()
        compose.onNodeWithText("Local · sample").assertExists()
        compose.onNodeWithContentDescription(searchLabel).performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("sample")
    }

    @Test fun dismissingViewOptionsRestoresFocusToTheTitleRow() {
        val optionsLabel = runBlocking { getString(Res.string.browse_options) }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlatform provides PlatformType.DESKTOP) {
                    BrowsePageHeader("Library") {
                        BrowseOptionsButton(active = true) { dismiss ->
                            TextButton(onClick = dismiss) { Text("Done") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(optionsLabel).assertIsSelected().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription(optionsLabel).assertIsFocused()
    }
}
