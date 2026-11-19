package snd.komelia.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.BrowseSearchHeader
import kotlin.test.assertEquals

class MacToolbarSearchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun activePageOwnsTheOnlySearchAndRestoresItsQuery() {
        val route = mutableStateOf("home")
        val libraryQuery = mutableStateOf("")
        val localQuery = mutableStateOf("")
        compose.setContent {
            val host = remember { MacToolbarSlotHost() }
            CompositionLocalProvider(LocalMacOS provides true, LocalMacToolbarSearch provides host) {
                KomeliaTheme(AppTheme.DARK) {
                    Column(Modifier.width(800.dp)) {
                        host.Content { Text("Global search") }
                        when (route.value) {
                            "library" -> BrowseSearchHeader("Library", libraryQuery.value, { libraryQuery.value = it }, placeholder = "Search library")
                            "local" -> BrowseSearchHeader("Local", localQuery.value, { localQuery.value = it }, placeholder = "Search local")
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Global search").assertExists()
        compose.runOnIdle { route.value = "library" }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText("Global search").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("rain")
        assertEquals("rain", libraryQuery.value)
        compose.runOnIdle { route.value = "local" }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNode(hasSetTextAction()).performTextInput("local")
        assertEquals("local", localQuery.value)
        compose.runOnIdle { route.value = "library" }
        compose.onNode(hasSetTextAction()).assertTextContains("rain")
        compose.runOnIdle { route.value = "home" }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Global search").assertExists()
    }
}
