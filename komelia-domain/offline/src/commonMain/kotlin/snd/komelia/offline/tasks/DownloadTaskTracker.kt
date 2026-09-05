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
        val updated = reduceDownloadTask(existing, event.toTaskUpdate())
        if (updated != existing) repository.save(updated)
    }
}

internal sealed interface DownloadTaskUpdate {
    data class Progress(
        val completedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long,
        val title: String,
    ) : DownloadTaskUpdate

    data class Completed(val title: String) : DownloadTaskUpdate

    data class Failed(
        val title: String?,
        val errorMessage: String,
    ) : DownloadTaskUpdate
}

private fun DownloadEvent.toTaskUpdate(): DownloadTaskUpdate = when (this) {
    is DownloadEvent.BookDownloadProgress -> DownloadTaskUpdate.Progress(
        completedBytes = completed,
        totalBytes = total,
        speedBytesPerSecond = speedBytesPerSecond,
        title = book.metadata.title,
    )
    is DownloadEvent.BookDownloadCompleted -> DownloadTaskUpdate.Completed(book.metadata.title)
    is DownloadEvent.BookDownloadError -> DownloadTaskUpdate.Failed(
        title = book?.metadata?.title,
        errorMessage = error.message ?: error::class.simpleName ?: "Download failed",
    )
}

internal fun reduceDownloadTask(
    existing: TaskEntry,
    update: DownloadTaskUpdate,
): TaskEntry {
    if (existing.status == PAUSED || existing.status == CANCELED) return existing

    return when (update) {
        is DownloadTaskUpdate.Progress -> existing.copy(
            status = RUNNING,
            completedBytes = maxOf(existing.completedBytes, update.completedBytes),
            totalBytes = maxOf(existing.totalBytes, update.totalBytes),
            speedBytesPerSecond = update.speedBytesPerSecond.coerceAtLeast(0),
            displayTitle = update.title,
            errorMessage = null,
        )
        is DownloadTaskUpdate.Completed -> existing.copy(
            status = COMPLETED,
            completedBytes = maxOf(existing.completedBytes, existing.totalBytes),
            speedBytesPerSecond = 0,
            displayTitle = update.title,
            errorMessage = null,
        )
        is DownloadTaskUpdate.Failed -> existing.copy(
            status = FAILED,
            speedBytesPerSecond = 0,
            displayTitle = update.title ?: existing.displayTitle,
            errorMessage = update.errorMessage,
        )
    }
}
