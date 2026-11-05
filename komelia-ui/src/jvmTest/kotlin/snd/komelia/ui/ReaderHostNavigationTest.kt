package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabDisposable
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.reader.EpubScreen
import snd.komelia.ui.reader.ImageReaderScreen
import snd.komga.client.book.KomgaBookId

class ReaderHostNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readingLeavesTheNestedBookPaneAndPreservesItsReturnStack() {
        lateinit var application: Navigator
        lateinit var readerHost: Navigator
        lateinit var bookPane: Navigator
        val tab = AppTab(AppDestination.LIBRARY, Marker("library"), listOf(Marker("series")))
        compose.setContent {
            Navigator(Marker("session")) { root ->
                Navigator(tab) { host ->
                    Navigator(listOf(Marker("series"), Marker("detail"))) { pane ->
                        SideEffect { application = root; readerHost = host; bookPane = pane }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertSame(readerHost, bookPane.readerHostNavigator())
            bookPane.readerHostNavigator().push(Marker("reader"))
            assertEquals("session", application.lastItem.key)
            assertEquals("reader", readerHost.lastItem.key)
            assertEquals(listOf("series", "detail"), bookPane.items.map { it.key })
            readerHost.pop()
            assertSame(tab, readerHost.lastItem)
            assertEquals("detail", bookPane.lastItem.key)
        }
    }

    private class Marker(override val key: String) : Screen {
        @Composable override fun Content() = Unit
    }

    @Test fun leavingTheBookPaneForAReaderDoesNotDisposeTheNewReaderModel() {
        val bookId = KomgaBookId("pane-book-${java.util.UUID.randomUUID()}")
        val disposed = mutableListOf<String>()
        val detailKey = BookScreen(bookId, BookSiblingsContext.Series).key
        val readerKey = EpubScreen(bookId, BookSiblingsContext.Series).key
        val tab = AppTab(AppDestination.LIBRARY, Marker("library"), listOf(Marker("series")))
        lateinit var pane: Navigator
        lateinit var host: Navigator
        compose.setContent {
            Navigator(Marker("session")) {
                TabNavigator(tab, disposeNestedNavigators = DISPOSE_DESTINATIONS_WHEN_HIDDEN,
                    tabDisposable = { TabDisposable(it, listOf(tab)) }) {
                    val root = LocalNavigator.currentOrThrow
                    SideEffect { host = root }
                    if (root.lastItem is AppTab) {
                        Navigator(listOf(Marker("library"), Marker("series")), key = "lifetime-destination") {
                            Navigator(ModelFixture(detailKey, "detail", disposed), key = "lifetime-pane") {
                                SideEffect { pane = it }
                                CurrentScreen()
                            }
                        }
                    } else CurrentScreen()
                }
            }
        }
        compose.runOnIdle { pane.readerHostNavigator().push(ModelFixture(readerKey, "reader", disposed)) }
        compose.runOnIdle {
            assertFalse("reader" in disposed, "Disposing the hidden detail navigator must not dispose the visible reader")
            host.pop()
        }
        compose.runOnIdle { assertSame(tab, host.lastItem) }
    }

    @Test fun normalAndIncognitoReadersHaveSeparateScreenModels() {
        val bookId = KomgaBookId("synthetic-book")
        val context = BookSiblingsContext.Series
        val regularEpub = EpubScreen(bookId, context, markReadProgress = true)
        val incognitoEpub = EpubScreen(bookId, context, markReadProgress = false)
        val regularImage = ImageReaderScreen(bookId, bookSiblingsContext = context, markReadProgress = true)
        val incognitoImage = ImageReaderScreen(bookId, bookSiblingsContext = context, markReadProgress = false)

        assertNotEquals(regularEpub.key, incognitoEpub.key)
        assertNotEquals(regularImage.key, incognitoImage.key)
    }

    private class ModelFixture(override val key: String, val tag: String, val disposed: MutableList<String>) : Screen {
        @Composable override fun Content() {
            rememberScreenModel(tag) { object : ScreenModel {
                override fun onDispose() { disposed += tag }
            } }
        }
    }
}
