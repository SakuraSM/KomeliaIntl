package snd.komelia.ui.settings.imagereader.onnxruntime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxProgressOperation.Status
import snd.komelia.updates.UpdateProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OnnxProgressOperationTest {
    @Test
    fun installIsLockedBeforeTheFirstProgressEvent() = runTest {
        val operation = OnnxProgressOperation()
        val firstEvent = CompletableDeferred<Unit>()
        var starts = 0
        val install = {
            starts++
            flow {
                firstEvent.await()
                emit(UpdateProgress(2, 2))
            }
        }

        operation.start(this, install)
        assertTrue(operation.isRunning)
        operation.start(this, install)
        runCurrent()

        assertEquals(1, starts)
        assertEquals(Status.Running(), operation.status)
        firstEvent.complete(Unit)
        runCurrent()
        assertEquals(Status.Succeeded, operation.status)
    }

    @Test
    fun downloadFailureRemainsVisibleAndCanBeRetried() = runTest {
        val operation = OnnxProgressOperation()
        operation.start(this) { flow { error("Synthetic download failure") } }
        runCurrent()
        assertEquals("Synthetic download failure", assertIs<Status.Failed>(operation.status).error.message)

        operation.start(this) { flowOf(UpdateProgress(4, 4)) }
        runCurrent()
        assertEquals(Status.Succeeded, operation.status)
    }

    @Test
    fun cancellingADownloadRunsCleanupWithoutReportingAnError() = runTest {
        val operation = OnnxProgressOperation()
        var cleanedUp = false
        operation.start(this) {
            flow {
                try {
                    emit(UpdateProgress(4, 1))
                    awaitCancellation()
                } finally {
                    cleanedUp = true
                }
            }
        }
        runCurrent()
        operation.cancel()
        runCurrent()

        assertTrue(cleanedUp)
        assertEquals(Status.Idle, operation.status)
    }
}
