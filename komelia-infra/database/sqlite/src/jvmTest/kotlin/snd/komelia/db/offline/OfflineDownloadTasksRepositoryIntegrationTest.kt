package snd.komelia.db.offline

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import snd.komelia.db.KomeliaDatabase
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.offline.tasks.model.TaskAddedEvent
import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskData.DownloadBookCancel
import snd.komelia.offline.tasks.model.TaskData.DownloadBookPause
import snd.komelia.offline.tasks.model.TaskData.DeleteBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.HIGHEST_PRIORITY
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.NEW
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.CANCELED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.PAUSED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.RUNNING
import snd.komga.client.book.KomgaBookId
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineDownloadTasksRepositoryIntegrationTest {
    @Test
    fun persistsDownloadStateAndSupportsPauseResumeAndHistoryRemoval() = runBlocking {
        val database = KomeliaDatabase(createTempDirectory("komelia-download-task-test").toString())
        val repository = ExposedOfflineTasksRepository(database.offline)
        val bookId = KomgaBookId("book-42")
        val download = DownloadBook(bookId)
        repository.save(
            TaskEntry(
                task = download,
                status = RUNNING,
                completedBytes = 42,
                totalBytes = 100,
                speedBytesPerSecond = 2_048,
                displayTitle = "Example",
            )
        )

        val reopenedRepository = ExposedOfflineTasksRepository(database.offline)
        assertEquals(
            listOf(Triple(RUNNING, 42L, "Example")),
            reopenedRepository.findDownloads().map { Triple(it.status, it.completedBytes, it.displayTitle) },
        )

        val emitter = OfflineTaskEmitter(
            tasksRepository = reopenedRepository,
            tasksFlow = MutableSharedFlow<TaskAddedEvent>(extraBufferCapacity = 4),
        )
        emitter.pauseBookDownload(bookId)
        assertEquals(PAUSED, reopenedRepository.find(download.uniqueName)?.status)
        assertEquals(NEW, reopenedRepository.find(DownloadBookPause(bookId).uniqueName)?.status)
        assertEquals(HIGHEST_PRIORITY, reopenedRepository.find(DownloadBookPause(bookId).uniqueName)?.priority)

        emitter.retryBookDownload(bookId)
        assertEquals(
            TaskEntry(task = download),
            reopenedRepository.find(download.uniqueName),
        )

        emitter.cancelBookDownload(bookId)
        assertEquals(CANCELED, reopenedRepository.find(download.uniqueName)?.status)
        assertEquals(NEW, reopenedRepository.find(DownloadBookCancel(bookId).uniqueName)?.status)
        assertEquals(HIGHEST_PRIORITY, reopenedRepository.find(DownloadBookCancel(bookId).uniqueName)?.priority)

        emitter.retryBookDownload(bookId)
        assertEquals(TaskEntry(task = download), reopenedRepository.find(download.uniqueName))

        emitter.removeDownloadTask(bookId)
        assertNull(reopenedRepository.find(download.uniqueName))

        repository.save(TaskEntry(task = download, status = RUNNING))
        emitter.removeDownloadTaskAndFiles(bookId)
        assertNull(reopenedRepository.find(download.uniqueName))
        assertEquals(NEW, reopenedRepository.find(DeleteBook(bookId).uniqueName)?.status)
    }

    @Test
    fun keepsOnePersistentEntryPerBookInALargeQueue() = runBlocking {
        val database = KomeliaDatabase(createTempDirectory("komelia-download-queue-test").toString())
        val repository = ExposedOfflineTasksRepository(database.offline)
        val tasks = List(100) { index ->
            TaskEntry(task = DownloadBook(KomgaBookId("book-$index")))
        }

        repository.save(tasks)
        repository.save(tasks.first().copy(displayTitle = "Updated"))

        assertEquals(100, repository.findDownloads().size)
        assertEquals("Updated", repository.find(tasks.first().uniqueName)?.displayTitle)
    }
}
