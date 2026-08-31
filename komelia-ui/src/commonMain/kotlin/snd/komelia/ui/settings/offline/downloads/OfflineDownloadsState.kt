package snd.komelia.ui.settings.offline.downloads

import coil3.PlatformContext
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.COMPLETED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.CANCELED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.FAILED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.PAUSED
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus.RUNNING
import snd.komelia.offline.tasks.repository.OfflineTasksRepository
import snd.komga.client.book.KomgaBookId

class OfflineDownloadsState(
    downloadEvents: SharedFlow<DownloadEvent>,
    platformContext: PlatformContext,
    private val taskEmitter: OfflineTaskEmitter,
    private val tasksRepository: OfflineTasksRepository,
    private val settingsRepository: OfflineSettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    private val internalDownloadDir = getDefaultInternalDownloadsDir(platformContext)
    private val tasksMap = MutableStateFlow<Map<KomgaBookId, TaskEntry>>(emptyMap())
    val downloads = tasksMap.map { tasks -> tasks.values.toList() }
        .stateIn(coroutineScope, SharingStarted.Eagerly, emptyList())

    val storageLocation = settingsRepository.getDownloadDirectory()
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    init {
        coroutineScope.launch { reload() }
        downloadEvents.onEach { event ->
            updateFromEvent(event)
        }.launchIn(coroutineScope)
    }

    fun onStorageLocationChange(directory: PlatformFile) {
        coroutineScope.launch { settingsRepository.putDownloadDirectory(directory) }
    }

    fun onStorageLocationReset() {
        coroutineScope.launch { settingsRepository.putDownloadDirectory(internalDownloadDir.platformFile) }
    }

    fun onDownloadPause(bookId: KomgaBookId) = launchAndReload { taskEmitter.pauseBookDownload(bookId) }
    fun onDownloadCancel(bookId: KomgaBookId) = launchAndReload { taskEmitter.cancelBookDownload(bookId) }
    fun onDownloadRetry(bookId: KomgaBookId) = launchAndReload { taskEmitter.retryBookDownload(bookId) }
    fun onTaskRemove(bookId: KomgaBookId) = launchAndReload { taskEmitter.removeDownloadTask(bookId) }
    fun onTaskRemoveWithFiles(bookId: KomgaBookId) = launchAndReload {
        taskEmitter.removeDownloadTaskAndFiles(bookId)
    }

    private fun launchAndReload(action: suspend () -> Unit) {
        coroutineScope.launch {
            action()
            reload()
        }
    }

    private suspend fun reload() {
        tasksMap.value = tasksRepository.findDownloads()
            .associateBy { (it.task as DownloadBook).bookId }
    }

    private fun updateFromEvent(event: DownloadEvent) {
        tasksMap.update { current ->
            val previous = current[event.bookId] ?: TaskEntry(task = DownloadBook(event.bookId))
            if (previous.status == PAUSED || previous.status == CANCELED) return@update current
            val updated = when (event) {
                is DownloadEvent.BookDownloadProgress -> previous.copy(
                    status = RUNNING,
                    completedBytes = maxOf(previous.completedBytes, event.completed),
                    totalBytes = event.total,
                    speedBytesPerSecond = event.speedBytesPerSecond,
                    displayTitle = event.book.metadata.title,
                    errorMessage = null,
                )
                is DownloadEvent.BookDownloadCompleted -> previous.copy(
                    status = COMPLETED,
                    completedBytes = previous.totalBytes,
                    speedBytesPerSecond = 0,
                    displayTitle = event.book.metadata.title,
                    errorMessage = null,
                )
                is DownloadEvent.BookDownloadError -> previous.copy(
                    status = FAILED,
                    speedBytesPerSecond = 0,
                    displayTitle = event.book?.metadata?.title ?: previous.displayTitle,
                    errorMessage = event.error.message ?: event.error::class.simpleName,
                )
            }
            current + (event.bookId to updated)
        }
    }
}

internal data class DefaultDownloadStorageLocation(
    val platformFile: PlatformFile,
    val label: String,
)

internal expect fun getDefaultInternalDownloadsDir(platformContent: PlatformContext): DefaultDownloadStorageLocation
