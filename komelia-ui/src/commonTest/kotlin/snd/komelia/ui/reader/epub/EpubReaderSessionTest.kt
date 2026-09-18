package snd.komelia.ui.reader.epub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class EpubReaderSessionTest {
    @Test fun closedDocumentCannotStartAnotherBridgeRequest() = runTest {
        val session = EpubReaderSession()
        var calls = 0
        session.close()
        assertFailsWith<CancellationException> { session.run { calls++ } }
        assertEquals(0, calls)
    }

    @Test fun completionAfterNativeCloseCannotReachTheNavigationCallback() = runTest {
        val session = EpubReaderSession()
        val response = CompletableDeferred<String>()
        var navigations = 0
        val pending = async { session.run { response.await() }.also { navigations++ } }
        runCurrent()
        session.close()
        response.complete("next book")
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(0, navigations)
    }

    @Test fun reentryCreatesANewSessionWithoutRevivingTheOldOne() = runTest {
        val old = EpubReaderSession()
        old.close()
        val current = EpubReaderSession()
        assertEquals("current book", current.run { "current book" })
        assertFailsWith<CancellationException> { old.run { "stale book" } }
    }
}
