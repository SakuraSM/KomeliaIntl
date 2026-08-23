package snd.komelia.ui.settings.offline.logs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import snd.komelia.offline.sync.model.OfflineLogEntry
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.ui.LoadState

class OfflineLogsState(
    private val logJournalRepository: LogJournalRepository,
    private val coroutineScope: CoroutineScope,
) {

    val logs = MutableStateFlow<List<OfflineLogEntry>>(emptyList())
    val loadState = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)

    val tab = MutableStateFlow(TaskTab.ERROR)
    val pageNumber = MutableStateFlow(1)
    val totalPages = MutableStateFlow(0)
    private val pageSize = 20

    suspend fun initialize() {
        loadTasks()
    }

    private suspend fun loadTasks() {
        loadState.value = LoadState.Loading
        try {
            val pageIndex = pageNumber.value - 1
            val type = when (tab.value) {
                TaskTab.INFO -> OfflineLogEntry.Type.INFO
                TaskTab.ERROR -> OfflineLogEntry.Type.ERROR
            }
            val page = logJournalRepository.findAllByType(
                type = type,
                limit = pageSize,
                offset = pageIndex.toLong() * pageSize,
            )
            logs.value = page.content
            pageNumber.value = page.number + 1
            totalPages.value = page.totalPages
            loadState.value = LoadState.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            logs.value = emptyList()
            totalPages.value = 0
            loadState.value = LoadState.Error(error)
        }
    }

    fun retry() {
        coroutineScope.launch { loadTasks() }
    }

    fun onPageChange(page: Int) {
        this.pageNumber.value = page
        coroutineScope.launch { loadTasks() }
    }

    fun onTabChange(tab: TaskTab) {
        this.tab.value = tab
        this.pageNumber.value = 1
        coroutineScope.launch { loadTasks() }
    }

    fun onLogsDelete() {
        coroutineScope.launch {
            try {
                logJournalRepository.deleteAll()
                pageNumber.value = 1
                loadTasks()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                loadState.value = LoadState.Error(error)
            }
        }
    }

    enum class TaskTab {
        ERROR,
        INFO,
    }

}
