package snd.komelia.offline.tasks

import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.CANCELED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.COMPLETED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.FAILED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.PAUSED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.RUNNING
import snd.komelia.offline.tasks.repository.OfflineTasksRepository

class DownloadTaskTracker(
    private val repository: OfflineTasksRepository,
) {
    suspend fun onEvent(event: DownloadEvent) {
        val task = DownloadBook(event.bookId)
        val existing = repository.find(task.uniqueName) ?: TaskEntry(task = task)
        if (existing.status == PAUSED || existing.status == CANCELED) return

        repository.save(
            when (event) {
                is DownloadEvent.BookDownloadProgress -> existing.copy(
                    status = RUNNING,
                    completedBytes = maxOf(existing.completedBytes, event.completed),
                    totalBytes = event.total,
                    speedBytesPerSecond = event.speedBytesPerSecond,
                    displayTitle = event.book.metadata.title,
                    errorMessage = null,
                )
                is DownloadEvent.BookDownloadCompleted -> existing.copy(
                    status = COMPLETED,
                    completedBytes = existing.totalBytes,
                    speedBytesPerSecond = 0,
                    displayTitle = event.book.metadata.title,
                    errorMessage = null,
                )
                is DownloadEvent.BookDownloadError -> existing.copy(
                    status = FAILED,
                    speedBytesPerSecond = 0,
                    displayTitle = event.book?.metadata?.title ?: existing.displayTitle,
                    errorMessage = event.error.message ?: event.error::class.simpleName,
                )
            }
        )
    }
}
