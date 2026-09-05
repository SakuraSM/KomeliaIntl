package snd.komelia.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_clear_query
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class SearchTextFieldTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longLocalPlaceholderKeepsMobileSearchHeight() {
        compose.setContent {
            CompositionLocalProvider(
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
            ) {
                MaterialTheme {
                    Box(Modifier.width(328.dp)) {
                        SearchTextField("", {}, {}, placeholder = "Search downloaded and locally imported books with a long title", modifier = Modifier.testTag("search"))
                    }
                }
            }
        }
        compose.onNodeWithTag("search").assertHeightIsEqualTo(48.dp)
    }

    @Test fun clearButtonClearsQueryWithoutChangingHeight() {
        val query = mutableStateOf("Reader")
        var clearLabel = ""
        compose.setContent {
            CompositionLocalProvider(
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
            ) {
                MaterialTheme {
                    clearLabel = stringResource(Res.string.search_clear_query)
                    Box(Modifier.width(328.dp)) {
                        SearchTextField(query.value, { query.value = it }, {}, onDismiss = { query.value = "" }, modifier = Modifier.testTag("search"))
                    }
                }
            }
        }
        compose.onNodeWithTag("search").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription(clearLabel).performClick()
        compose.runOnIdle { assertEquals("", query.value) }
        compose.onNodeWithTag("search").assertHeightIsEqualTo(48.dp)
    }
}
