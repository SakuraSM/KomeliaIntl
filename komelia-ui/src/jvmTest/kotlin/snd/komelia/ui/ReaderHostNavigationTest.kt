package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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
import kotlin.test.assertTrue
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
        val created = mutableListOf<String>()
        val detailKey = BookScreen(bookId, BookSiblingsContext.Series).key
        fun readerKey() = EpubScreen(bookId, BookSiblingsContext.Series).key
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
                            Navigator(ModelFixture(detailKey, "detail", disposed, created), key = "lifetime-pane") {
                                SideEffect { pane = it }
                                CurrentScreen()
                            }
                        }
                    } else ImmersiveScreen(root, root.lastItem)
                }
            }
        }
        compose.runOnIdle { pane.readerHostNavigator().push(ModelFixture(readerKey(), "reader", disposed, created)) }
        compose.runOnIdle {
            assertFalse("reader" in disposed, "Disposing the hidden detail navigator must not dispose the visible reader")
            host.push(ModelFixture("reader-settings", "settings", disposed, created))
        }
        compose.runOnIdle {
            assertFalse("reader" in disposed, "A reader still in the stack must survive its settings screen")
            host.pop()
        }
        compose.runOnIdle {
            assertTrue("settings" in disposed, "Popping reader settings must retire its screen model")
            assertFalse("reader" in disposed)
            host.pop()
        }
        compose.runOnIdle {
            assertSame(tab, host.lastItem)
            assertTrue("reader" in disposed, "Popping the reader must retire its screen model")
            host.push(ModelFixture(readerKey(), "reader", disposed, created))
        }
        compose.runOnIdle {
            assertEquals(2, created.count { it == "reader" }, "Reopening must create a new reader model")
            host.pop()
            host.push(ModelFixture(readerKey(), "reader", disposed, created))
        }
        compose.runOnIdle {
            assertEquals(3, created.count { it == "reader" }, "An immediate reopen must not reuse the retired reader")
        }
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

    @Test fun readerKeysAreUniquePerSessionAndSurviveSerialization() {
        val bookId = KomgaBookId("synthetic-book")
        val context = BookSiblingsContext.Series
        val image = ImageReaderScreen(bookId, bookSiblingsContext = context)
        val epub = EpubScreen(bookId, context)
        assertNotEquals(image.key, ImageReaderScreen(bookId, bookSiblingsContext = context).key)
        assertNotEquals(epub.key, EpubScreen(bookId, context).key)
        assertEquals(image.key, roundTrip(image).key)
        assertEquals(epub.key, roundTrip(epub).key)
    }

    @Test fun previouslySavedReaderScreensCanStillBeRestored() {
        // Synthetic screens serialized before session IDs were added.
        val fixture = checkNotNull(javaClass.getResourceAsStream("/synthetic-reader-screens-before-session-id.ser"))
        ObjectInputStream(fixture).use { input ->
            val image = input.readObject() as ImageReaderScreen
            val epub = input.readObject() as EpubScreen
            assertTrue(image.key.startsWith("image-reader:synthetic-book:tracked:"))
            assertTrue(epub.key.startsWith("epub-reader:synthetic-book:tracked:"))
            assertEquals(image.key, roundTrip(image).key)
            assertEquals(epub.key, roundTrip(epub).key)
        }
    }

    private fun <T : Screen> roundTrip(screen: T): T {
        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(screen) } }
        @Suppress("UNCHECKED_CAST")
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as T }
    }

    private class ModelFixture(
        override val key: String,
        val tag: String,
        val disposed: MutableList<String>,
        val created: MutableList<String>,
    ) : Screen {
        @Composable override fun Content() {
            rememberScreenModel(tag) { created += tag; object : ScreenModel {
                override fun onDispose() { disposed += tag }
            } }
        }
    }
}
