package snd.komelia.ui.book

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.*
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.sse.KomgaEvent
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class BookProgressRefreshTest {
    @Test fun returningFromReaderRefreshesProgressWithoutAnEvent() = verifyRefresh(false)
    @Test fun initialDetailSnapshotDoesNotOverrideSavedProgress() = verifyRefresh(true)

    private fun verifyRefresh(staleInitially: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = book()
        var saved = snapshot
        val events = MutableSharedFlow<KomgaEvent>()
        val holder = "book-progress-${java.util.UUID.randomUUID()}"
        val vm = ScreenModelStore.getOrPut(holder, null) { BookViewModel(
            book = snapshot, bookId = snapshot.id,
            bookApi = stub { name -> when (name) {
                "getOne" -> saved
                "getAllReadListsByBook" -> emptyList<Any>()
                else -> error("Unexpected call $name")
            } },
            libraryApi = stub { throw IOException("No synthetic library") },
            notifications = AppNotifications(), komgaEvents = events,
            libraries = MutableStateFlow(emptyList()), taskEmitter = null, localLibraryManager = null,
            settingsRepository = stub { flowOf(240) },
            readListApi = stub { error("No synthetic read lists") },
        ) }
        try {
            if (!staleInitially) {
                vm.initialize()
                advanceUntilIdle()
                vm.stopKomgaEventHandler()
            }
            saved = snapshot.copy(readProgress = snapshot.readProgress!!.copy(page = 2))
            vm.startKomgaEventsHandler()
            vm.initialize()
            advanceUntilIdle()
            assertEquals(2, vm.book.value!!.readProgress!!.page)
            // Returning must not add another pair of SSE subscribers.
            val subscriptions = events.subscriptionCount.value
            vm.initialize()
            advanceUntilIdle()
            assertEquals(subscriptions, events.subscriptionCount.value)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private fun book(): KomeliaBook {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(
            id = KomgaBookId("book"), seriesId = KomgaSeriesId("series"), seriesTitle = "Synthetic",
            libraryId = KomgaLibraryId("library"), name = "Synthetic", url = "synthetic://book", number = 1,
            created = now, lastModified = now, fileLastModified = now, sizeBytes = 20, size = "20 B",
            media = Media(KomgaMediaStatus.READY, "application/zip", 3, "", false, false, MediaProfile.DIVINA),
            metadata = KomgaBookMetadata("Synthetic", "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, now, now),
            readProgress = ReadProgress(1, false, now, "test", "test", now, now),
            deleted = false, fileHash = "", oneshot = false, downloaded = true,
            localFileLastModified = now, remoteFileUnavailable = false,
        )
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as T
}
