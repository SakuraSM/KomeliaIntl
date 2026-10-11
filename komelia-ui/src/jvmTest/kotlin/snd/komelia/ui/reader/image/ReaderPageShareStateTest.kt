package snd.komelia.ui.reader.image

import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.image.PageShareService
import snd.komelia.image.SharedPageImage
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageShareStateTest {
    @Test
    fun sharingCapturesTheClickedPageAndIgnoresRepeatedClicksWhilePreparing() = runTest {
        val sourceBytes = byteArrayOf(1, 2, 3)
        val requests = mutableListOf<Pair<String, Int>>()
        val shared = mutableListOf<SharedPageImage>()
        val release = CompletableDeferred<Unit>()
        val state = ReaderPageShareState(
            bookApi { book, page -> requests.add(book to page); sourceBytes },
            PageShareService { shared.add(it); release.await() },
            AppNotifications(), backgroundScope,
        )
        state.share(KomgaBookId("book-A"), 7)
        state.share(KomgaBookId("book-B"), 2)
        runCurrent()
        assertEquals(listOf("book-A" to 7), requests)
        assertSame(sourceBytes, shared.single().encodedImage)
        assertEquals(7, shared.single().pageNumber)
        assertTrue(state.isSharing.value)
        release.complete(Unit)
        runCurrent()
        assertFalse(state.isSharing.value)
        state.share(KomgaBookId("book-B"), 2)
        runCurrent()
        assertEquals(listOf("book-A" to 7, "book-B" to 2), requests)
    }

    @Test
    fun failedPageFetchDoesNotOpenAShareSheetAndCanBeRetried() = runTest {
        var shouldFail = true
        var shares = 0
        val notifications = AppNotifications()
        val state = ReaderPageShareState(
            bookApi { _, _ -> if (shouldFail) throw IOException("Synthetic fetch failure") else byteArrayOf(1) },
            PageShareService { shares++ }, notifications, backgroundScope,
        )
        state.share(KomgaBookId("book"), 2)
        state.isSharing.first { !it }
        assertEquals(0, shares)
        assertFalse(state.isSharing.value)
        assertIs<AppNotification.Error>(notifications.getNotifications().first().single())
        shouldFail = false
        state.share(KomgaBookId("book"), 2)
        state.isSharing.first { !it }
        assertEquals(1, shares)
    }

    @Test
    fun leavingTheReaderCancelsPendingSharingWithoutAnErrorToast() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val notifications = AppNotifications()
        val pending = CompletableDeferred<Unit>()
        val state = ReaderPageShareState(bookApi { _, _ -> byteArrayOf(1) },
            PageShareService { pending.await() }, notifications, scope)
        state.share(KomgaBookId("book"), 1)
        runCurrent()
        scope.cancel()
        advanceUntilIdle()
        assertFalse(state.isSharing.value)
        assertTrue(notifications.getNotifications().first().isEmpty())
    }

    @Test
    fun aNewShareStartedOnCompletionKeepsItsBusyGuard() = runTest {
        val requests = mutableListOf<Int>()
        val pending = CompletableDeferred<Unit>()
        val state = ReaderPageShareState(bookApi { _, page -> requests.add(page); byteArrayOf(1) },
            PageShareService { if (it.pageNumber == 2) pending.await() }, AppNotifications(), backgroundScope)
        var queued = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            state.isSharing.drop(1).collect { busy ->
                if (!busy && !queued) {
                    queued = true
                    state.share(KomgaBookId("book"), 2)
                }
            }
        }
        state.share(KomgaBookId("book"), 1)
        runCurrent()
        assertEquals(listOf(1, 2), requests)
        assertTrue(state.isSharing.value)
        state.share(KomgaBookId("book"), 3)
        runCurrent()
        assertEquals(listOf(1, 2), requests)
        pending.complete(Unit)
        runCurrent()
        assertFalse(state.isSharing.value)
    }

    private fun bookApi(fetch: (String, Int) -> ByteArray): KomgaBookApi =
        Proxy.newProxyInstance(KomgaBookApi::class.java.classLoader, arrayOf(KomgaBookApi::class.java)) { _, method, arguments ->
            check(method.name.startsWith("getPage"))
            fetch(arguments[0] as String, arguments[1] as Int)
        } as KomgaBookApi

    @Test
    fun alreadyClosedReaderDoesNotLeaveSharingBusy() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        scope.cancel()
        val notifications = AppNotifications()
        val state = ReaderPageShareState(bookApi { _, _ -> error("Closed reader must not fetch") },
            PageShareService { error("Closed reader must not share") }, notifications, scope)
        state.share(KomgaBookId("book"), 1)
        advanceUntilIdle()
        assertFalse(state.isSharing.value)
        assertTrue(notifications.getNotifications().first().isEmpty())
    }
}
