package snd.komelia.ui.reader.epub

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtuExitSessionTest {
    @Test fun requestsWaitForTheReaderAndIgnoreRepeatedBackPresses() = runTest {
        val session = TtuExitSession()
        assertFalse(session.requestExit())
        val first = async(start = CoroutineStart.UNDISPATCHED) { session.awaitRequest() }
        assertTrue(session.requestExit())
        assertTrue(session.requestExit())
        assertTrue(first.await())

        val next = async(start = CoroutineStart.UNDISPATCHED) { session.awaitRequest() }
        runCurrent()
        assertFalse(next.isCompleted)
        assertTrue(session.requestExit())
        assertTrue(next.await())
    }

    @Test fun closingTheSessionReleasesItsWaitingReader() = runTest {
        val session = TtuExitSession()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { session.awaitRequest() }
        assertTrue(session.retire())
        assertFalse(waiting.await())
        assertFalse(session.requestExit())
        assertFalse(session.awaitRequest())
        assertFalse(session.retire())
    }
}
