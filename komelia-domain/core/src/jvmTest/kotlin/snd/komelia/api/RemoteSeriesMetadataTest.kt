package snd.komelia.api

import java.lang.reflect.Proxy
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.Json
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komf.client.KomfMangaBakaClient
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.common.Page
import snd.komga.client.collection.KomgaCollectionClient
import snd.komga.client.collection.KomgaCollectionId
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesBookMetadata
import snd.komga.client.series.KomgaSeriesClient
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadata
import snd.komga.client.series.KomgaSeriesStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class RemoteSeriesMetadataTest {
    @Test
    fun legacySeriesStateCanBeRestoredWithoutMangaBakaMetadata() {
        val original = syntheticSeries()
        val restored = Json.decodeFromString<KomeliaSeries>(Json.encodeToString(original))
        assertEquals(original.id, restored.id)
        assertNull(restored.mangaBakaMetadata)
    }

    @Test
    fun unavailableMetadataDoesNotDiscardTheLoadedSeries() = runTest {
        val original = syntheticSeries()
        val catalog = stub<KomgaSeriesClient> { original }
        HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { error("Synthetic metadata outage") }
            }
        }.use { http ->
            val metadata = KomfMangaBakaClient(http)
            val result = RemoteSeriesApi(catalog, flowOf(metadata)).getOneSeries(original.id)
            assertEquals(original.id, result.id)
            assertEquals(original.metadata, result.metadata)
            assertEquals(original.booksInProgressCount, result.booksInProgressCount)
            assertNull(result.mangaBakaMetadata)
            assertEquals(0, testScheduler.currentTime)
        }
    }

    @Test
    fun disabledMetadataUsesOnlyTheCatalog() = runTest {
        val original = syntheticSeries()
        var catalogCalls = 0
        val catalog = stub<KomgaSeriesClient> { catalogCalls++; original }
        val result = RemoteSeriesApi(catalog, flowOf(null)).getOneSeries(original.id)

        assertEquals(1, catalogCalls)
        assertEquals(original.id, result.id)
        assertNull(result.mangaBakaMetadata)
    }

    @Test
    fun catalogFailureStillFailsTheRequest() = runTest {
        val catalog = stub<KomgaSeriesClient> { error("Synthetic catalog outage") }
        assertFailsWith<IllegalStateException> {
            RemoteSeriesApi(catalog, flowOf(null)).getOneSeries(KomgaSeriesId("series"))
        }
    }

    @Test
    fun metadataCancellationIsNotConvertedToSuccess() = runTest {
        val original = syntheticSeries()
        val catalog = stub<KomgaSeriesClient> { original }
        HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { throw CancellationException("Reader closed") }
            }
        }.use { http ->
            val metadata = KomfMangaBakaClient(http)
            assertFailsWith<CancellationException> {
                RemoteSeriesApi(catalog, flowOf(metadata)).getOneSeries(original.id)
            }
        }
    }

    @Test
    fun seriesAndCollectionPagesKeepOrderingAndPaginationDuringMetadataOutage() = runTest {
        val first = syntheticSeries()
        val second = first.copy(id = KomgaSeriesId("second-series"))
        val original = Page.empty<KomgaSeries>().copy(
            content = listOf(second, first), totalElements = 30, totalPages = 3,
            number = 1, first = false, last = false, size = 10, numberOfElements = 2, empty = false,
        )
        HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { error("Synthetic metadata outage") }
            }
        }.use { http ->
            val metadata = flowOf(KomfMangaBakaClient(http))
            val series = RemoteSeriesApi(stub<KomgaSeriesClient> { original }, metadata)
                .getNewSeries(null, null, null, null)
            val collection = RemoteCollectionsApi(stub<KomgaCollectionClient> { original }, metadata)
                .getSeriesForCollection(KomgaCollectionId("collection"), null, null)
            for (result in listOf(series, collection)) {
                assertEquals(original.content.map { it.id }, result.content.map { it.id })
                assertEquals(original.totalElements, result.totalElements)
                assertEquals(original.totalPages, result.totalPages)
                assertEquals(original.number, result.number)
                assertEquals(original.pageable, result.pageable)
                assertEquals(original.sort, result.sort)
                assertEquals(original.size, result.size)
                assertEquals(original.last, result.last)
                assertEquals(original.first, result.first)
            }
        }
    }

    private inline fun <reified Contract> stub(crossinline answer: (String) -> Any?): Contract =
        Proxy.newProxyInstance(Contract::class.java.classLoader, arrayOf(Contract::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as Contract
}

internal fun syntheticSeries(): KomgaSeries {
    val timestamp = Instant.fromEpochMilliseconds(1_000)
    return KomgaSeries(
        id = KomgaSeriesId("synthetic-series"),
        libraryId = KomgaLibraryId("synthetic-library"),
        name = "Synthetic series",
        url = "local://synthetic",
        booksCount = 3,
        booksReadCount = 1,
        booksUnreadCount = 1,
        booksInProgressCount = 1,
        metadata = KomgaSeriesMetadata(
            status = KomgaSeriesStatus.ONGOING, statusLock = false,
            title = "Synthetic series", titleLock = false,
            alternateTitles = emptyList(), alternateTitlesLock = false,
            titleSort = "Synthetic series", titleSortLock = false,
            summary = "Synthetic metadata", summaryLock = false,
            readingDirection = KomgaReadingDirection.LEFT_TO_RIGHT, readingDirectionLock = false,
            publisher = "", publisherLock = false,
            ageRating = null, ageRatingLock = false,
            language = "", languageLock = false,
            genres = emptyList(), genresLock = false,
            tags = emptyList(), tagsLock = false,
            totalBookCount = null, totalBookCountLock = false,
            sharingLabels = emptyList(), sharingLabelsLock = false,
            links = emptyList(), linksLock = false,
        ),
        deleted = false,
        oneshot = false,
        booksMetadata = KomgaSeriesBookMetadata(emptyList(), emptyList(), null, "", "", timestamp, timestamp),
        created = timestamp,
        lastModified = timestamp,
        fileLastModified = timestamp,
    )
}
