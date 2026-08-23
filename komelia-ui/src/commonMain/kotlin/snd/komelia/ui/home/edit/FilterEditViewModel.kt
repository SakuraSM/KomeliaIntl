package snd.komelia.ui.home.edit

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komelia.homefilters.HomeScreenFilterRepository
import snd.komelia.homefilters.SeriesHomeScreenFilter
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaCollectionsApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.cards.defaultCardWidth
import snd.komelia.ui.home.BookFilterData
import snd.komelia.ui.home.HomeFilterData
import snd.komelia.ui.home.SeriesFilterData
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibrary

class FilterEditViewModel(
    private val initialFilters: List<HomeFilterData>?,
    private val appNotifications: AppNotifications,
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val readListApi: KomgaReadListApi,
    private val collectionApi: KomgaCollectionsApi,
    private val referentialApi: KomgaReferentialApi,
    private val filterRepository: HomeScreenFilterRepository,
    private val libraries: Flow<List<KomgaLibrary>>,
    cardWidthFlow: Flow<Dp>,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {
    private val filterSuggestionOptions = MutableStateFlow(
        FilterSuggestionOptions(
            tags = emptyList(),
            genres = emptyList(),
            authors = emptyList(),
            publishers = emptyList(),
            languages = emptyList(),
            libraries = emptyList(),
            sharingLabels = emptyList()
        )
    )

    val cardWidth = cardWidthFlow.stateIn(screenModelScope, Eagerly, defaultCardWidth.dp)
    val filters = MutableStateFlow(

        initialFilters?.map {
            when (it) {
                is BookFilterData -> BookFilterEditState(
                    seriesApi = seriesApi,
                    bookApi = bookApi,
                    readListApi = readListApi,
                    appNotifications = appNotifications,
                    coroutineScope = screenModelScope,
                    options = filterSuggestionOptions,
                    cardWidth = cardWidth,
                    initialFilter = it.filter,
                    initialBooks = it.books,
                )

                is SeriesFilterData -> SeriesFilterEditState(
                    seriesApi = seriesApi,
                    collectionApi = collectionApi,
                    appNotifications = appNotifications,
                    coroutineScope = screenModelScope,
                    options = filterSuggestionOptions,
                    cardWidth = cardWidth,
                    initialFilter = it.filter,
                    initialSeries = it.series,
                )
            }
        } ?: emptyList()
    )


    suspend fun initialize() {
        appNotifications.runCatchingToNotifications {
            if (initialFilters == null) {
                // FIXME initial data will remain empty until filter is modified
                filters.value = filterRepository.getFilters().first()
                    .map { it.withLocalizedDefaultLabel() }
                    .map {
                        when (it) {
                            is BooksHomeScreenFilter -> BookFilterEditState(
                                seriesApi = seriesApi,
                                bookApi = bookApi,
                                readListApi = readListApi,
                                appNotifications = appNotifications,
                                coroutineScope = screenModelScope,
                                options = filterSuggestionOptions,
                                cardWidth = cardWidth,
                                initialFilter = it,
                                initialBooks = null,
                            )

                            is SeriesHomeScreenFilter -> SeriesFilterEditState(
                                seriesApi = seriesApi,
                                collectionApi = collectionApi,
                                appNotifications = appNotifications,
                                coroutineScope = screenModelScope,
                                options = filterSuggestionOptions,
                                cardWidth = cardWidth,
                                initialFilter = it,
                                initialSeries = null,
                            )
                        }
                    }
            }

            val tags = referentialApi.getBookTags()
            val genres = referentialApi.getGenres()
            val authors = referentialApi.getAuthors(pageRequest = KomgaPageRequest(unpaged = true)).content
            val sharingLabels = referentialApi.getSharingLabels()
            val languages = referentialApi.getLanguages()
            val publishers = referentialApi.getPublishers()
            filterSuggestionOptions.value = FilterSuggestionOptions(
                tags = tags,
                genres = genres,
                libraries = libraries.first(),
                authors = authors,
                sharingLabels = sharingLabels,
                languages = languages,
                publishers = publishers
            )
        }
    }

    fun onFilterReorder(from: Int, to: Int) {
        filters.update { current -> moveItemSafely(current, from, to) }
    }

    fun onFilterAdd(type: FilterType, localizedLabel: String) {
        val newFilter = when (type) {
            FilterType.Series -> SeriesFilterEditState(
                seriesApi = seriesApi,
                collectionApi = collectionApi,
                appNotifications = appNotifications,
                coroutineScope = screenModelScope,
                options = filterSuggestionOptions,
                cardWidth = cardWidth,
                initialFilter = null,
                initialSeries = null,
                newFilterLabel = localizedLabel,
            )

            FilterType.Book -> BookFilterEditState(
                seriesApi = seriesApi,
                bookApi = bookApi,
                readListApi = readListApi,
                appNotifications = appNotifications,
                coroutineScope = screenModelScope,
                options = filterSuggestionOptions,
                cardWidth = cardWidth,
                initialFilter = null,
                initialBooks = null,
                newFilterLabel = localizedLabel,
            )
        }
        filters.update { current -> current.plus(newFilter) }
    }

    fun onFilterRemove(filter: FilterEditState) {
        filters.update { current -> current.minus(filter) }

    }

    fun onResetFiltersToDefault() {
        screenModelScope.launch {
            val defaultFilters = getDefaultFilters()
            appNotifications.runCatchingToNotifications {
                filterRepository.putFilters(defaultFilters)
                filters.value = defaultFilters.map {
                    when (it) {
                        is BooksHomeScreenFilter -> BookFilterEditState(
                            seriesApi = seriesApi,
                            bookApi = bookApi,
                            readListApi = readListApi,
                            appNotifications = appNotifications,
                            coroutineScope = screenModelScope,
                            options = filterSuggestionOptions,
                            cardWidth = cardWidth,
                            initialFilter = it,
                            initialBooks = null,
                        )

                        is SeriesHomeScreenFilter -> SeriesFilterEditState(
                            seriesApi = seriesApi,
                            collectionApi = collectionApi,
                            appNotifications = appNotifications,
                            coroutineScope = screenModelScope,
                            options = filterSuggestionOptions,
                            cardWidth = cardWidth,
                            initialFilter = it,
                            initialSeries = null,
                        )
                    }
                }
            }
        }
    }

    suspend fun onEditEnd(): Result<Unit> = appNotifications.runCatchingToNotifications {
        filterRepository.putFilters(
            filters.value.mapIndexed { index, state -> state.toFilter(index + 1) },
        )
    }

    enum class FilterType {
        Series, Book
    }
}

internal fun <T> moveItemSafely(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items

    return items.toMutableList().apply {
        add(to, removeAt(from))
    }
}
