package snd.komelia.offline.tasks

import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.CANCELED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.COMPLETED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.FAILED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.PAUSED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.RUNNING
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadTaskTrackerTest {
    private val task = DownloadBook(KomgaBookId("book-1"))

    @Test
    fun progressNeverMovesBackwards() {
        val existing = TaskEntry(
            task = task,
            status = RUNNING,
            completedBytes = 80,
            totalBytes = 100,
            speedBytesPerSecond = 10,
        )

        val updated = reduceDownloadTask(
            existing,
            DownloadTaskUpdate.Progress(
                completedBytes = 60,
                totalBytes = 90,
                speedBytesPerSecond = 8,
                title = "Book",
            ),
        )

        assertEquals(80L, updated.completedBytes)
        assertEquals(100L, updated.totalBytes)
        assertEquals(8L, updated.speedBytesPerSecond)
        assertEquals("Book", updated.displayTitle)
    }

    @Test
    fun pausedAndCanceledTasksIgnoreLateWorkerEvents() {
        val update = DownloadTaskUpdate.Progress(50, 100, 12, "Book")
        val paused = TaskEntry(task = task, status = PAUSED, completedBytes = 20)
        val canceled = TaskEntry(task = task, status = CANCELED, completedBytes = 30)

        assertEquals(paused, reduceDownloadTask(paused, update))
        assertEquals(canceled, reduceDownloadTask(canceled, update))
    }

    @Test
    fun completionAndFailurePreserveUsefulPersistentState() {
        val running = TaskEntry(
            task = task,
            status = RUNNING,
            completedBytes = 75,
            totalBytes = 100,
            displayTitle = "Existing title",
        )

        val completed = reduceDownloadTask(running, DownloadTaskUpdate.Completed("Final title"))
        assertEquals(COMPLETED, completed.status)
        assertEquals(100L, completed.completedBytes)
        assertEquals("Final title", completed.displayTitle)

        val failed = reduceDownloadTask(
            running,
            DownloadTaskUpdate.Failed(title = null, errorMessage = "Network error"),
        )
        assertEquals(FAILED, failed.status)
        assertEquals(75L, failed.completedBytes)
        assertEquals("Existing title", failed.displayTitle)
        assertEquals("Network error", failed.errorMessage)
    }
}
