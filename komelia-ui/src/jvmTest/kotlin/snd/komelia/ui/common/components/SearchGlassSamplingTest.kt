package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.search.SearchTextField
import kotlin.test.assertTrue

class SearchGlassSamplingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editingPausesCaptureAndReleasingFocusRestoresIt() {
        lateinit var budget: GlassSamplingBudget
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPlatform provides PlatformType.MOBILE,
                    LocalWindowWidth provides WindowSizeClass.COMPACT,
                    LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
                ) {
                    GlassBackdropScope {
                        budget = requireNotNull(LocalGlassSamplingBudget.current)
                        val focus = LocalFocusManager.current
                        Column {
                            SearchTextField("Chapter", {}, {}, placeholder = "Search library")
                            TextButton(onClick = { focus.clearFocus(force = true) }) { Text("Done") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Search library").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(budget.isPaused, "Focused editing must not keep recapturing the backdrop") }
        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(timeoutMillis = 2_000) { !budget.isPaused }
    }
}
