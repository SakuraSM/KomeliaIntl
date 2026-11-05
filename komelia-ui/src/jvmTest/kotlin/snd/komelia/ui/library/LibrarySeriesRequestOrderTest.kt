package snd.komelia.ui.library

import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.time.Instant
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LoadState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.SeriesConditionBuilder
import snd.komga.client.series.KomgaSeriesBookMetadata
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadata
import snd.komga.client.series.KomgaSeriesStatus

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class LibrarySeriesRequestOrderTest {
    @Test fun lateRefreshCannotReplaceNewSearchResults() = verifyLateRefresh(fails = false)

    @Test fun lateRefreshFailureCannotReplaceNewSearchResults() = verifyLateRefresh(fails = true)

    @Test fun currentRequestFailureStillShowsAnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val failure = IOException("Current request failed")
        val api = object : KomgaSeriesApi by stub({ error("Unexpected series API call: $it") }) {
            override suspend fun getSeriesList(
                conditionBuilder: SeriesConditionBuilder,
                fulltextSearch: String?,
                pageRequest: KomgaPageRequest?,
            ): Page<KomeliaSeries> = throw failure
        }
        val notifications = AppNotifications()
        val holder = "library-current-error-${java.util.UUID.randomUUID()}"
        val model = ScreenModelStore.getOrPut(holder, null) {
            LibrarySeriesTabState(
                seriesApi = api,
                referentialApi = stub { emptyList<String>() },
                notifications = notifications,
                komgaEvents = MutableSharedFlow(),
                settingsRepository = stub { flowOf(50) },
                library = MutableStateFlow(null),
                taskEmitter = null,
                cardWidth = MutableStateFlow(120.dp),
            )
        }
        try {
            model.initialize()
            advanceUntilIdle()
            assertEquals(failure, assertIs<LoadState.Error>(model.state.value).exception)
            assertEquals(1, notifications.getNotifications().first().size)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private fun verifyLateRefresh(fails: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val delayed = CompletableDeferred<Page<KomeliaSeries>>()
        var initialLoaded = false
        val api = object : KomgaSeriesApi by stub({ error("Unexpected series API call: $it") }) {
            override suspend fun getSeriesList(
                conditionBuilder: SeriesConditionBuilder,
                fulltextSearch: String?,
                pageRequest: KomgaPageRequest?,
            ): Page<KomeliaSeries> {
                if (fulltextSearch == "needle") return page(series("matching"))
                if (!initialLoaded) {
                    initialLoaded = true
                    return page(series("initial"))
                }
                return delayed.await()
            }
        }
        val notifications = AppNotifications()
        val holder = "library-request-order-${java.util.UUID.randomUUID()}"
        val model = ScreenModelStore.getOrPut(holder, null) {
            LibrarySeriesTabState(
                seriesApi = api,
                referentialApi = stub { emptyList<String>() },
                notifications = notifications,
                komgaEvents = MutableSharedFlow(),
                settingsRepository = stub { flowOf(50) },
                library = MutableStateFlow(null),
                taskEmitter = null,
                cardWidth = MutableStateFlow(120.dp),
            )
        }
        try {
            model.initialize()
            advanceUntilIdle()
            assertEquals("", model.loadedFilter?.searchTerm)
            model.reload()
            runCurrent()
            model.filterState.onSearchTermChange("needle")
            assertNotEquals(model.filterState.state.value, model.loadedFilter)
            runCurrent()
            assertEquals("matching", model.series.single().id.value)
            assertEquals("needle", model.loadedFilter?.searchTerm)
            advanceTimeBy(250)
            runCurrent()
            assertIs<LoadState.Success<Unit>>(model.state.value)

            if (fails) delayed.completeExceptionally(IOException("Earlier request failed"))
            else delayed.complete(page(series("outdated")))
            advanceUntilIdle()

            assertIs<LoadState.Success<Unit>>(model.state.value)
            assertEquals("matching", model.series.single().id.value)
            assertEquals("needle", model.loadedFilter?.searchTerm)
            assertEquals(1, model.totalSeriesCount)
            assertEquals(0, notifications.getNotifications().first().size)
        } finally {
            delayed.cancel()
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private fun page(series: KomeliaSeries) = Page.empty<KomeliaSeries>().copy(
        content = listOf(series), totalElements = 1, totalPages = 1, numberOfElements = 1, empty = false,
    )

    private fun series(id: String): KomeliaSeries {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaSeries(
            id = KomgaSeriesId(id), libraryId = KomgaLibraryId("library"), name = id, url = "synthetic://$id",
            booksCount = 1, booksReadCount = 0, booksUnreadCount = 1, booksInProgressCount = 0,
            metadata = KomgaSeriesMetadata(
                status = KomgaSeriesStatus.ONGOING, statusLock = false, title = id, titleLock = false,
                alternateTitles = emptyList(), alternateTitlesLock = false, titleSort = id, titleSortLock = false,
                summary = "", summaryLock = false, readingDirection = null, readingDirectionLock = false,
                publisher = "", publisherLock = false, ageRating = null, ageRatingLock = false,
                language = "", languageLock = false, genres = emptyList(), genresLock = false,
                tags = emptyList(), tagsLock = false, totalBookCount = null, totalBookCountLock = false,
                sharingLabels = emptyList(), sharingLabelsLock = false, links = emptyList(), linksLock = false,
            ),
            deleted = false, oneshot = false,
            booksMetadata = KomgaSeriesBookMetadata(emptyList(), emptyList(), null, "", "", now, now),
            created = now, lastModified = now, fileLastModified = now, mangaBakaMetadata = null,
        )
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as T
}
