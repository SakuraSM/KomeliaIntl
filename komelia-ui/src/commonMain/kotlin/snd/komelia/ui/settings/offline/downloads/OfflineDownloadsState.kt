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
import kotlinx.coroutines.launch
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
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
        // The tracker persists each event before publishing it. Reloading the repository here keeps
        // the UI aligned with restart recovery, pause/cancel guards, and background workers.
        downloadEvents.onEach { reload() }.launchIn(coroutineScope)
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

}

internal data class DefaultDownloadStorageLocation(
    val platformFile: PlatformFile,
    val label: String,
)

internal expect fun getDefaultInternalDownloadsDir(platformContent: PlatformContext): DefaultDownloadStorageLocation
