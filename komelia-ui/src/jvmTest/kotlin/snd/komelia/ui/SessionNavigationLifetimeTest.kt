package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SessionNavigationLifetimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun logoutDisposesLibraryModelsBeforeAnotherServerUsesTheSameScreenKey() {
        val observed = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        lateinit var root: Navigator
        compose.setContent {
            Navigator(SessionScreen("A", observed, disposed)) { navigator ->
                SideEffect { root = navigator }
                CurrentScreen()
            }
        }
        compose.runOnIdle { assertEquals("A", observed.last()); root.replaceAll(LoginFixture) }
        compose.runOnIdle {
            assertEquals(listOf("A"), disposed)
            root.replaceAll(SessionScreen("B", observed, disposed))
        }
        compose.runOnIdle { assertEquals("B", observed.last()) }
    }

    private class SessionScreen(
        val server: String, val observed: MutableList<String>, val disposed: MutableList<String>,
        val readerVisible: MutableState<Boolean> = mutableStateOf(false),
    ) : Screen {
        override val key = MainScreen().key
        @Composable override fun Content() {
            val session = rememberScreenModel("session-navigators") { SessionNavigators() }
            TabNavigator(
                LibraryTabFixture,
                disposeNestedNavigators = DISPOSE_DESTINATIONS_WHEN_HIDDEN,
                tabDisposable = { TabDisposable(it, listOf(LibraryTabFixture)) },
                key = "$key-destinations",
            ) {
                if (!readerVisible.value) {
                    Navigator(LibraryFixture(server, observed, disposed), key = "$key-library") { navigator ->
                        SideEffect { session.register(AppDestination.LIBRARY, navigator) }
                        CurrentScreen()
                    }
                }
            }
        }
    }

    @Test fun openingAReaderRetainsTheDestinationUntilTheSessionEnds() {
        val observed = mutableListOf<String>()
        val disposed = mutableListOf<String>()
        val readerVisible = mutableStateOf(false)
        lateinit var root: Navigator
        compose.setContent {
            Navigator(SessionScreen("A", observed, disposed, readerVisible)) { navigator ->
                SideEffect { root = navigator }
                CurrentScreen()
            }
        }
        compose.runOnIdle { readerVisible.value = true }
        compose.runOnIdle { assertEquals(emptyList(), disposed); readerVisible.value = false }
        compose.runOnIdle { assertEquals("A", observed.last()); assertEquals(emptyList(), disposed) }
        compose.runOnIdle { readerVisible.value = true }
        compose.runOnIdle { root.replaceAll(LoginFixture) }
        compose.runOnIdle { assertEquals(listOf("A"), disposed) }
    }

    private class LibraryFixture(
        val server: String, val observed: MutableList<String>, val disposed: MutableList<String>,
    ) : Screen {
        override val key = "all-libraries-fixture"
        @Composable override fun Content() {
            val model = rememberScreenModel { LibraryModel(server, disposed) }
            SideEffect { observed += model.server }
        }
    }

    private class LibraryModel(val server: String, val disposed: MutableList<String>) : ScreenModel {
        override fun onDispose() { disposed += server }
    }
    private object LoginFixture : Screen { @Composable override fun Content() = Unit }
    private object LibraryTabFixture : Tab {
        override val options: TabOptions @Composable get() = TabOptions(0u, "Library")
        @Composable override fun Content() = Unit
    }
}
