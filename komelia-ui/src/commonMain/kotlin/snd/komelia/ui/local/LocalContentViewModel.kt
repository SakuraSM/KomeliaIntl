package snd.komelia.ui.local

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.AvailableBookSource
import snd.komelia.offline.local.AvailableBooksRepository
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.home.LocalHomeBookSort
import snd.komelia.ui.home.pageRequest

internal const val LOCAL_CONTENT_PAGE_SIZE = 20

@OptIn(FlowPreview::class)
class LocalContentViewModel(
    private val localLibraryManager: LocalLibraryManager?,
    private val availableBooksRepository: AvailableBooksRepository?,
    private val bookApi: KomgaBookApi,
    private val appNotifications: AppNotifications,
    private val taskEmitter: OfflineTaskEmitter?,
    cardWidthFlow: Flow<Dp>,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)
    val books = MutableStateFlow(emptyList<KomeliaBook>())
    val query = MutableStateFlow("")
    val source = MutableStateFlow(AvailableBookSource.ALL)
    val sort = MutableStateFlow(LocalHomeBookSort.RECENTLY_ADDED)
    val currentPage = MutableStateFlow(1)
    val totalPages = MutableStateFlow(1)
    private val sourceRefresh = LocalContentRefreshCoordinator(
        scanSources = { localLibraryManager?.scanAll() },
        reloadIndex = { loadPage(currentPage.value) },
    )

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        loadPage(1)
        screenModelScope.launch {
            combine(query.debounce(350), source, sort) { query, source, sort ->
                LocalContentQuery(query.trim(), source, sort)
            }
                .distinctUntilChanged()
                .drop(1)
                .collect { loadPage(1) }
        }
        localLibraryManager?.let { manager ->
            screenModelScope.launch {
                manager.scanState
                    .drop(1)
                    .collect { scanState ->
                        if (
                            !sourceRefresh.isRefreshing &&
                            scanState.scanningLibraryId == null &&
                            scanState.error == null
                        ) {
                            loadPage(1)
                        }
                    }
            }
        }
    }

    fun reload() {
        screenModelScope.launch { loadPage(currentPage.value) }
    }

    fun refreshFromSources() {
        screenModelScope.launch {
            appNotifications.runCatchingToNotifications {
                sourceRefresh.refresh {
                    mutableState.value = LoadState.Loading
                }
            }.onFailure { mutableState.value = LoadState.Error(it) }
        }
    }

    fun onPageChange(page: Int) {
        if (page == currentPage.value) return
        screenModelScope.launch { loadPage(page) }
    }

    fun onSourceChange(value: AvailableBookSource) {
        source.value = value
    }

    fun onSortChange(value: LocalHomeBookSort) {
        sort.value = value
    }

    private suspend fun loadPage(page: Int) {
        mutableState.value = LoadState.Loading
        val result = appNotifications.runCatchingToNotifications {
            availableBooksRepository?.getBooks(
                source = source.value,
                query = query.value,
                pageRequest = sort.value.pageRequest().copy(
                    pageIndex = (page - 1).coerceAtLeast(0),
                    size = LOCAL_CONTENT_PAGE_SIZE,
                    unpaged = false,
                ),
            )
        }
        result.onSuccess { loadedPage ->
            books.value = loadedPage?.content.orEmpty()
            currentPage.value = loadedPage?.number?.plus(1) ?: 1
            totalPages.value = loadedPage?.totalPages?.coerceAtLeast(1) ?: 1
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun bookMenuActions() = BookMenuActions(
        bookApi = bookApi,
        notifications = appNotifications,
        scope = screenModelScope,
        taskEmitter = taskEmitter,
        localLibraryManager = localLibraryManager,
        onReadProgressChanged = { reload() },
    )
}

internal data class LocalContentQuery(
    val query: String,
    val source: AvailableBookSource,
    val sort: LocalHomeBookSort,
)

internal class LocalContentRefreshCoordinator(
    private val scanSources: suspend () -> Unit,
    private val reloadIndex: suspend () -> Unit,
) {
    private val mutex = Mutex()
    val isRefreshing: Boolean get() = mutex.isLocked

    suspend fun refresh(onStart: () -> Unit = {}): Boolean {
        if (!mutex.tryLock()) return false
        return try {
            onStart()
            scanSources()
            reloadIndex()
            true
        } finally {
            mutex.unlock()
        }
    }
}
