package snd.komelia.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.ui.LoadState
import snd.komelia.ui.home.bookHomeComparator
import snd.komelia.ui.home.localBookSearch
import snd.komelia.ui.home.localSeriesSearch
import snd.komelia.ui.home.seriesHomeComparator
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesSearch

private val logger = KotlinLogging.logger { }

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val seriesApi: KomgaSeriesApi,
    private val bookApi: KomgaBookApi,
    private val offlineSeriesApi: KomgaSeriesApi?,
    private val offlineBookApi: KomgaBookApi?,
    private val localLibraryManager: LocalLibraryManager?,
    private val appNotifications: AppNotifications,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {

    var seriesResults by mutableStateOf<List<KomgaSeries>>(emptyList())
        private set
    var seriesCurrentPage by mutableStateOf(1)
        private set
    var seriesTotalPages by mutableStateOf(1)
        private set
    var seriesCoverage by mutableStateOf(SearchCoverage.COMPLETE)
        private set

    var bookResults by mutableStateOf<List<KomeliaBook>>(emptyList())
        private set
    var bookCurrentPage by mutableStateOf(1)
        private set
    var bookTotalPages by mutableStateOf(1)
        private set
    var bookCoverage by mutableStateOf(SearchCoverage.COMPLETE)
        private set

    var query by mutableStateOf("")

    private var userSelectedTab by mutableStateOf(SearchResultsTab.SERIES)
    var currentTab by mutableStateOf(SearchResultsTab.SERIES)
        private set

    val currentCoverage: SearchCoverage
        get() = when (currentTab) {
            SearchResultsTab.SERIES -> seriesCoverage
            SearchResultsTab.BOOKS -> bookCoverage
        }

    suspend fun initialize(initialQuery: String?) {
        if (state.value != LoadState.Uninitialized && initialQuery == query) return
        initialQuery?.let { query = it }
        loadSearchResults()

        screenModelScope.launch {
            snapshotFlow { query }
                .drop(1)
                .debounce { if (it.isBlank()) 0 else 500 }
                .distinctUntilChanged()
                .collectLatest { loadSearchResults() }
        }
    }

    fun reload() {
        screenModelScope.launch { loadSearchResults() }
    }

    private suspend fun loadSearchResults() {
        mutableState.value = LoadState.Loading
        currentTab = userSelectedTab
        val localLibraryIds = localLibraryIds()
        appNotifications.runCatchingToNotifications {
            coroutineScope {
                val series = async { fetchSeriesPage(1, localLibraryIds) }
                val books = async { fetchBookPage(1, localLibraryIds) }
                series.await() to books.await()
            }
        }.onSuccess { (series, books) ->
            applySeriesPage(series)
            applyBookPage(books)
            if (seriesResults.isEmpty() && bookResults.isNotEmpty() && currentTab == SearchResultsTab.SERIES) {
                currentTab = SearchResultsTab.BOOKS
            }
            mutableState.value = LoadState.Success(Unit)
        }.onFailure { mutableState.value = LoadState.Error(it) }
    }

    fun onSeriesPageChange(pageNumber: Int) {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            appNotifications.runCatchingToNotifications {
                fetchSeriesPage(pageNumber, localLibraryIds())
            }.onSuccess {
                applySeriesPage(it)
                mutableState.value = LoadState.Success(Unit)
            }.onFailure { mutableState.value = LoadState.Error(it) }
        }
    }

    fun onBookPageChange(pageNumber: Int) {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            appNotifications.runCatchingToNotifications {
                fetchBookPage(pageNumber, localLibraryIds())
            }.onSuccess {
                applyBookPage(it)
                mutableState.value = LoadState.Success(Unit)
            }.onFailure { mutableState.value = LoadState.Error(it) }
        }
    }

    private suspend fun fetchSeriesPage(
        pageNumber: Int,
        localLibraryIds: Result<List<KomgaLibraryId>>,
    ): SearchLoadResult<KomgaSeries> {
        val sort = KomgaSort.KomgaSeriesSort.byLastModifiedDateDesc()
        val pageRequest = searchPageRequest(pageNumber, sort)
        val search = KomgaSeriesSearch(fullTextSearch = query)
        val localSearch = localLibraryIds.getOrNull()?.let { localSeriesSearch(search, it) }
        return loadUnifiedSearchPage(
            pageNumber = pageNumber,
            idOf = { it.id },
            comparator = requireNotNull(seriesHomeComparator(sort)),
            remoteLoad = { seriesApi.getSeriesList(search, pageRequest).toSearchSourcePage() },
            localLoad = when {
                offlineSeriesApi == null -> null
                localLibraryIds.isFailure -> {
                    val exception = requireNotNull(localLibraryIds.exceptionOrNull())
                    suspend { throw exception }
                }

                localSearch == null -> null
                else -> suspend { offlineSeriesApi.getSeriesList(localSearch, pageRequest).toSearchSourcePage() }
            },
            offlineFallback = offlineSeriesApi?.let { api ->
                suspend { api.getSeriesList(search, pageRequest).toSearchSourcePage() }
            },
        )
    }

    private suspend fun fetchBookPage(
        pageNumber: Int,
        localLibraryIds: Result<List<KomgaLibraryId>>,
    ): SearchLoadResult<KomeliaBook> {
        val sort = KomgaSort.KomgaBooksSort.byLastModifiedDateDesc()
        val pageRequest = searchPageRequest(pageNumber, sort)
        val search = KomgaBookSearch(fullTextSearch = query)
        val localSearch = localLibraryIds.getOrNull()?.let { localBookSearch(search, it) }
        return loadUnifiedSearchPage(
            pageNumber = pageNumber,
            idOf = { it.id },
            comparator = requireNotNull(bookHomeComparator(sort)),
            remoteLoad = { bookApi.getBookList(search, pageRequest).toSearchSourcePage() },
            localLoad = when {
                offlineBookApi == null -> null
                localLibraryIds.isFailure -> {
                    val exception = requireNotNull(localLibraryIds.exceptionOrNull())
                    suspend { throw exception }
                }

                localSearch == null -> null
                else -> suspend { offlineBookApi.getBookList(localSearch, pageRequest).toSearchSourcePage() }
            },
            offlineFallback = offlineBookApi?.let { api ->
                suspend { api.getBookList(search, pageRequest).toSearchSourcePage() }
            },
        )
    }

    private fun applySeriesPage(result: SearchLoadResult<KomgaSeries>) {
        seriesResults = result.page.content
        seriesCurrentPage = result.page.currentPage
        seriesTotalPages = result.page.totalPages
        seriesCoverage = result.coverage
    }

    private fun applyBookPage(result: SearchLoadResult<KomeliaBook>) {
        bookResults = result.page.content
        bookCurrentPage = result.page.currentPage
        bookTotalPages = result.page.totalPages
        bookCoverage = result.coverage
    }

    private suspend fun localLibraryIds(): Result<List<KomgaLibraryId>> = runCatching {
        localLibraryManager?.getLibraries()?.map { it.id }.orEmpty()
    }.onFailure { logger.catching(it) }

    fun onSearchTypeChange(type: SearchResultsTab) {
        currentTab = type
        userSelectedTab = type
    }

    enum class SearchResultsTab {
        SERIES,
        BOOKS,
    }
}

private fun searchPageRequest(pageNumber: Int, sort: KomgaSort): KomgaPageRequest = KomgaPageRequest(
    pageIndex = 0,
    size = searchFetchSize(pageNumber),
    sort = sort,
)

private fun <T> Page<T>.toSearchSourcePage(): SearchSourcePage<T> = SearchSourcePage(
    content = content,
    totalElements = totalElements,
)

internal suspend fun <T, K> loadUnifiedSearchPage(
    pageNumber: Int,
    idOf: (T) -> K,
    comparator: Comparator<T>,
    remoteLoad: suspend () -> SearchSourcePage<T>,
    localLoad: (suspend () -> SearchSourcePage<T>)?,
    offlineFallback: (suspend () -> SearchSourcePage<T>)?,
): SearchLoadResult<T> = coroutineScope {
    val remote = async { runCatching { remoteLoad() }.onFailure { logger.catching(it) } }
    val local = async {
        localLoad?.let { runCatching { it() }.onFailure { exception -> logger.catching(exception) } }
            ?: Result.success(SearchSourcePage(emptyList(), 0))
    }
    val remoteResult = remote.await()
    val localResult = local.await()

    if (remoteResult.isSuccess) {
        return@coroutineScope SearchLoadResult(
            page = mergeSearchPages(
                remote = remoteResult.getOrThrow(),
                local = localResult.getOrDefault(SearchSourcePage(emptyList(), 0)),
                pageNumber = pageNumber,
                idOf = idOf,
                comparator = comparator,
            ),
            coverage = if (localResult.isSuccess) SearchCoverage.COMPLETE else SearchCoverage.REMOTE_ONLY,
        )
    }

    val fallback = offlineFallback
        ?: throw requireNotNull(remoteResult.exceptionOrNull())
    val offlineResult = runCatching { fallback() }.onFailure { logger.catching(it) }
    SearchLoadResult(
        page = mergeSearchPages(
            remote = SearchSourcePage(emptyList(), 0),
            local = offlineResult.getOrElse { throw requireNotNull(remoteResult.exceptionOrNull()) },
            pageNumber = pageNumber,
            idOf = idOf,
            comparator = comparator,
        ),
        coverage = SearchCoverage.OFFLINE_ONLY,
    )
}

data class SearchResults(
    val series: List<KomgaSeries>,
    val books: List<KomeliaBook>,
)
