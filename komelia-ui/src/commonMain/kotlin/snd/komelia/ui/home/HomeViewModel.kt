package snd.komelia.ui.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilterRepository
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.home.edit.withLocalizedDefaultLabel
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.series.KomgaSeriesSearch
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.BookEvent
import snd.komga.client.sse.KomgaEvent.ReadProgressEvent
import snd.komga.client.sse.KomgaEvent.ReadProgressSeriesEvent
import snd.komga.client.sse.KomgaEvent.SeriesEvent

private val logger = KotlinLogging.logger { }

class HomeViewModel(
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val offlineSeriesApi: KomgaSeriesApi?,
    private val offlineBookApi: KomgaBookApi?,
    private val appNotifications: AppNotifications,
    private val komgaEvents: SharedFlow<KomgaEvent>,
    private val filterRepository: HomeScreenFilterRepository,
    private val taskEmitter: OfflineTaskEmitter?,
    private val localLibraryManager: LocalLibraryManager?,
    cardWidthFlow: Flow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)

    private val reloadEventsEnabled = MutableStateFlow(true)
    private val reloadJobsFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    val currentFilters = MutableStateFlow(emptyList<HomeFilterData>())
    val activeFilterNumber = MutableStateFlow(0)

    suspend fun initialize() {
        if (state.value !is Uninitialized) return

        mutableState.value = LoadState.Loading
        screenModelScope.launch {
            homeConfigurationRefreshFlow(filterRepository.getFilters(), reloadJobsFlow)
                .collectLatest(::load)
        }
        startKomgaEventListener()
        startLocalLibraryScanListener()
    }

    fun reload() {
        reloadJobsFlow.tryEmit(Unit)
    }

    private suspend fun load(filters: List<HomeScreenFilter>) {
        appNotifications.runCatchingToNotifications {
            mutableState.value = LoadState.Loading

            val localLibraryIds = runCatching {
                localLibraryManager?.getLibraries()?.map { it.id }.orEmpty()
            }.onFailure { logger.catching(it) }.getOrDefault(emptyList())
            val loadedFilters = coroutineScope {
                orderedHomeScreenFilters(filters)
                    .map { it.withLocalizedDefaultLabel() }
                    .map { async { fetchFilterData(it, localLibraryIds) } }
                    .awaitAll()
                    .filterNotNull()
            }
            currentFilters.value = loadedFilters
            activeFilterNumber.value = reconcileActiveHomeFilter(
                activeFilterNumber = activeFilterNumber.value,
                availableFilterNumbers = currentFilters.value.map { it.filter.order },
            )

            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    private suspend fun fetchFilterData(
        filter: HomeScreenFilter,
        localLibraryIds: List<snd.komga.client.library.KomgaLibraryId>,
    ): HomeFilterData? {
        return when (filter) {
            is BooksHomeScreenFilter.CustomFilter -> {
                val search = KomgaBookSearch(filter.filter, filter.textSearch)
                val remote = bookApi.getBookList(search, filter.pageRequest).content
                val local = localBookSearch(search, localLibraryIds)?.let { localSearch ->
                    runCatching { offlineBookApi?.getBookList(localSearch, filter.pageRequest)?.content.orEmpty() }
                        .onFailure { logger.catching(it) }
                        .getOrDefault(emptyList())
                }.orEmpty()
                BookFilterData(
                    books = mergeHomeItems(
                        remote = remote,
                        local = local,
                        limit = filter.pageRequest?.size ?: maxOf(remote.size, local.size),
                        idOf = { it.id },
                        comparator = bookHomeComparator(filter.pageRequest?.sort),
                    ),
                    filter = filter,
                )
            }

            is BooksHomeScreenFilter.OnDeck -> {
                val pageRequest = KomgaPageRequest(
                    size = filter.pageSize,
                    sort = snd.komga.client.common.KomgaSort.KomgaBooksSort.byReadDateDesc(),
                )
                val remote = bookApi.getBooksOnDeck(pageRequest = pageRequest).content
                val local = if (localLibraryIds.isEmpty()) emptyList() else {
                    runCatching {
                        offlineBookApi?.getBooksOnDeck(localLibraryIds, pageRequest)?.content.orEmpty()
                    }.onFailure { logger.catching(it) }.getOrDefault(emptyList())
                }
                BookFilterData(
                    mergeHomeItems(
                        remote,
                        local,
                        filter.pageSize,
                        { it.id },
                        bookHomeComparator(pageRequest.sort),
                    ),
                    filter,
                )
            }

            is SeriesHomeScreenFilter.CustomFilter -> {
                val search = KomgaSeriesSearch(filter.filter, filter.textSearch)
                val remote = seriesApi.getSeriesList(search, filter.pageRequest).content
                val local = localSeriesSearch(search, localLibraryIds)?.let { localSearch ->
                    runCatching { offlineSeriesApi?.getSeriesList(localSearch, filter.pageRequest)?.content.orEmpty() }
                        .onFailure { logger.catching(it) }
                        .getOrDefault(emptyList())
                }.orEmpty()
                SeriesFilterData(
                    series = mergeHomeItems(
                        remote = remote,
                        local = local,
                        limit = filter.pageRequest?.size ?: maxOf(remote.size, local.size),
                        idOf = { it.id },
                        comparator = seriesHomeComparator(filter.pageRequest?.sort),
                    ),
                    filter = filter,
                )
            }

            is SeriesHomeScreenFilter.RecentlyAdded -> {
                val pageRequest = KomgaPageRequest(
                    size = filter.pageSize,
                    sort = snd.komga.client.common.KomgaSort.KomgaSeriesSort.byCreatedDateDesc(),
                )
                val remote = seriesApi.getNewSeries(
                    oneshot = false,
                    pageRequest = pageRequest,
                ).content
                val local = if (localLibraryIds.isEmpty()) emptyList() else {
                    runCatching {
                        offlineSeriesApi?.getNewSeries(
                            libraryIds = localLibraryIds,
                            oneshot = false,
                            pageRequest = pageRequest,
                        )?.content.orEmpty()
                    }.onFailure { logger.catching(it) }.getOrDefault(emptyList())
                }
                SeriesFilterData(
                    series = mergeHomeItems(
                        remote,
                        local,
                        filter.pageSize,
                        { it.id },
                        seriesHomeComparator(pageRequest.sort),
                    ),
                    filter = filter,
                )
            }

            is SeriesHomeScreenFilter.RecentlyUpdated -> {
                val pageRequest = KomgaPageRequest(
                    size = filter.pageSize,
                    sort = snd.komga.client.common.KomgaSort.KomgaSeriesSort.byLastModifiedDateDesc(),
                )
                val remote = seriesApi.getUpdatedSeries(
                    oneshot = false,
                    pageRequest = pageRequest,
                ).content
                val local = if (localLibraryIds.isEmpty()) emptyList() else {
                    runCatching {
                        offlineSeriesApi?.getUpdatedSeries(
                            libraryIds = localLibraryIds,
                            oneshot = false,
                            pageRequest = pageRequest,
                        )?.content.orEmpty()
                    }.onFailure { logger.catching(it) }.getOrDefault(emptyList())
                }
                SeriesFilterData(
                    series = mergeHomeItems(
                        remote,
                        local,
                        filter.pageSize,
                        { it.id },
                        seriesHomeComparator(pageRequest.sort),
                    ),
                    filter = filter,
                )
            }
        }

    }

    fun seriesMenuActions() = SeriesMenuActions(seriesApi, appNotifications, taskEmitter, screenModelScope)
    fun bookMenuActions() = BookMenuActions(
        bookApi = bookApi,
        notifications = appNotifications,
        scope = screenModelScope,
        taskEmitter = taskEmitter,
        localLibraryManager = localLibraryManager,
        onReadProgressChanged = { reload() },
    )

    fun stopKomgaEventsHandler() {
        reloadEventsEnabled.value = false
    }

    fun startKomgaEventsHandler() {
        reloadEventsEnabled.value = true
    }

    private fun startLocalLibraryScanListener() {
        val manager = localLibraryManager ?: return
        screenModelScope.launch {
            var previous = manager.scanState.value
            manager.scanState.collect { current ->
                if (didLocalLibraryScanFinish(previous, current)) reloadJobsFlow.emit(Unit)
                previous = current
            }
        }
    }

    private fun startKomgaEventListener() {
        screenModelScope.launch {
            komgaEvents.collect { event ->
                when (event) {
                    is BookEvent,
                    is SeriesEvent,
                    is ReadProgressEvent,
                    is ReadProgressSeriesEvent -> {
                        reloadEventsEnabled.first { it }
                        reloadJobsFlow.emit(Unit)
                        delay(5000)
                    }

                    else -> {}
                }
            }
        }
    }

    fun onFilterChange(number: Int) {
        this.activeFilterNumber.value = number
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> homeConfigurationRefreshFlow(
    configurations: Flow<T>,
    reloads: Flow<Unit>,
): Flow<T> = configurations.flatMapLatest { configuration ->
    reloads
        .map { configuration }
        .onStart { emit(configuration) }
}

internal fun reconcileActiveHomeFilter(
    activeFilterNumber: Int,
    availableFilterNumbers: Collection<Int>,
): Int = activeFilterNumber.takeIf {
    it == HOME_ALL_TAB_ID || it in availableFilterNumbers
} ?: HOME_ALL_TAB_ID

internal fun orderedHomeScreenFilters(filters: List<HomeScreenFilter>): List<HomeScreenFilter> =
    filters.sortedBy { it.order }
