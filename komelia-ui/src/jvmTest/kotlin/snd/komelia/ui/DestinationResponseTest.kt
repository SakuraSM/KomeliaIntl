package snd.komelia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import org.junit.Rule
import org.junit.Test

class DestinationResponseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingADestinationShowsItOnTheNextFrameInsteadOfWaitingForFadeOut() {
        val home = tab(AppDestination.HOME)
        val library = tab(AppDestination.LIBRARY)
        val selected = mutableStateOf(home)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                SingleLayerDestinationTransition(selected.value) { Text(it.destination.name) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("HOME").assertIsDisplayed()
        compose.runOnIdle { selected.value = library }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("LIBRARY").assertIsDisplayed()
        compose.onNodeWithText("HOME").assertDoesNotExist()
    }

    @Test fun rapidChoicesNeverLeaveAnOlderDestinationVisible() {
        val selected = mutableStateOf(tab(AppDestination.HOME))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                SingleLayerDestinationTransition(selected.value) { Text(it.destination.name) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { selected.value = tab(AppDestination.LIBRARY) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { selected.value = tab(AppDestination.LOCAL) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
    }

    @Test fun detailNavigationAlsoShowsTheRequestedScreenOnTheNextFrame() {
        lateinit var navigator: Navigator
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                Navigator(MarkerScreen("first")) {
                    navigator = it
                    DestinationContent(it)
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { navigator.push(MarkerScreen("second")) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("second").assertIsDisplayed()
        compose.onNodeWithText("first").assertDoesNotExist()
    }

    private class MarkerScreen(private val label: String) : Screen {
        override val key = label
        @androidx.compose.runtime.Composable override fun Content() { Text(label) }
    }

    private fun tab(destination: AppDestination): AppTab {
        val screen = object : Screen {
            @androidx.compose.runtime.Composable override fun Content() = Unit
        }
        return AppTab(destination, screen, listOf(screen))
    }
}
