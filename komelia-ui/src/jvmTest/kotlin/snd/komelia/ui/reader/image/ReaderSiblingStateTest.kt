package snd.komelia.ui.reader.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komga.client.book.*
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.*
import kotlin.time.Instant

class ReaderSiblingStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedAdjacentLookupAndRetryKeepTheCurrentBookAndPage() {
        lateinit var navigator: Navigator
        compose.setContent { Navigator(EmptyScreen) { nav -> SideEffect { navigator = nav } } }
        compose.waitForIdle()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val first = book("chapter-1", 1f)
        val second = book("chapter-1.5", 1.5f)
        var fail = true
        var emptyNextPages = true
        val api = stub<KomgaBookApi> { name, args ->
            when (name) {
                "getBookSiblingNext" -> if (args.first() == first.id.value) {
                    if (fail) throw IOException("synthetic timeout") else second
                } else null
                "getBookSiblingPrevious" -> if (args.first() == second.id.value) first else null
                "getBookPages" -> if (emptyNextPages && args.first() == second.id.value) emptyList() else
                    listOf(1, 2).map { KomgaBookPage(it, "$it.png", "image/png", 1000, 1500, 10L, "10 B") }
                else -> error("Unexpected API call $name")
            }
        }
        val reader = ReaderState(
            initialBook = first, bookApi = api, seriesApi = stub { _, _ -> throw IOException("no synthetic series metadata") },
            readListApi = stub { _, _ -> error("not a read list") }, navigator = navigator,
            appNotifications = AppNotifications(), readerSettingsRepository = stub { name, _ ->
                flowOf(when (name) {
                    "getUpsamplingMode" -> UpsamplingMode.NEAREST
                    "getDownsamplingKernel" -> ReduceKernel.NEAREST
                    "getFlashDuration" -> 100L
                    "getFlashEveryNPages" -> 1
                    "getFlashWith" -> ReaderFlashColor.BLACK
                    "getReaderType" -> ReaderType.PAGED
                    "getStretchToFit" -> true
                    else -> false
                })
            }, currentBookId = MutableStateFlow(null), markReadProgress = false, stateScope = scope,
            bookSiblingsContext = BookSiblingsContext.Series,
            colorCorrectionRepository = stub { _, _ -> error("not needed") }, pageChangeFlow = MutableSharedFlow(),
        )
        try {
            runBlocking {
                reader.initialize(first.id)
                assertIs<LoadState.Success<Unit>>(reader.state.value)
                assertIs<SiblingLoad.Failed>(reader.booksState.value!!.next)
                reader.onProgressChange(2)
                val pages = reader.booksState.value!!.currentBookPages
                fail = false
                reader.retrySibling(next = true)
                assertIs<SiblingLoad.Failed>(reader.booksState.value!!.next)
                assertEquals(first.id, reader.booksState.value!!.currentBook.id)
                assertEquals(2, reader.readProgressPage.value)
                emptyNextPages = false
                reader.retrySibling(next = true)
                assertEquals(first.id, reader.booksState.value!!.currentBook.id)
                assertSame(pages, reader.booksState.value!!.currentBookPages)
                assertEquals(2, reader.readProgressPage.value)
                assertNull(reader.retryingSibling.value)
                assertEquals(second.id, reader.booksState.value!!.nextBook?.id)
                reader.loadNextBook()
                assertEquals(second.id, reader.booksState.value!!.currentBook.id)
                assertEquals(1, reader.readProgressPage.value)
                assertSame(SiblingLoad.End, reader.booksState.value!!.next)
                reader.loadPreviousBook()
                assertEquals(first.id, reader.booksState.value!!.currentBook.id)
                assertEquals(2, reader.readProgressPage.value)
            }
        } finally {
            reader.onDispose()
            scope.cancel()
        }
    }

    private fun book(id: String, number: Float): KomeliaBook {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(
            id = KomgaBookId(id), seriesId = KomgaSeriesId("series"), seriesTitle = "Synthetic series",
            libraryId = KomgaLibraryId("library"), name = id, url = "synthetic://$id", number = 1,
            created = now, lastModified = now, fileLastModified = now, sizeBytes = 20, size = "20 B",
            media = Media(KomgaMediaStatus.READY, "application/zip", 2, "", false, false, MediaProfile.DIVINA),
            metadata = KomgaBookMetadata(id, "", number.toString(), number, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, now, now),
            readProgress = null, deleted = false, fileHash = "", oneshot = false, downloaded = true,
            localFileLastModified = now, remoteFileUnavailable = false,
        )
    }

    private object EmptyScreen : Screen { @Composable override fun Content() = Unit }

    private inline fun <reified T> stub(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            answer(method.name.substringBefore('-'), args.orEmpty())
        } as T
}
