package snd.komelia.ui.oneshot

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LoadState
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadata
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.book.Media
import snd.komga.client.book.MediaProfile
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.library.ScanInterval
import snd.komga.client.library.SeriesCover
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesBookMetadata
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadata
import snd.komga.client.series.KomgaSeriesStatus
import snd.komga.client.sse.KomgaEvent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class OneshotEmptySeriesTest {
    @Test
    fun emptySeriesCanBeReloadedWithoutAnExceptionOrErrorToast() = verifyEmptySeries(false)

    @Test
    fun failedLookupRemainsAnErrorUntilASuccessfulRetry() = verifyEmptySeries(true)

    private fun verifyEmptySeries(failInitially: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val notifications = AppNotifications()
        val events = MutableSharedFlow<KomgaEvent>()
        var shouldFail = failInitially
        var lookups = 0
        var availableBook: KomeliaBook? = null
        val series = series()
        val owner = "oneshot-empty-${java.util.UUID.randomUUID()}"
        val model = ScreenModelStore.getOrPut(owner, null) {
            OneshotViewModel(
                series = series,
                book = null,
                seriesId = series.id,
                seriesApi = stub { series },
                bookApi = stub {
                    when (it) {
                        "getBookList" -> {
                            lookups++
                            if (shouldFail) throw IOException("Synthetic lookup failure")
                            val books = listOfNotNull(availableBook)
                            Page.empty<KomeliaBook>().copy(content = books, totalElements = books.size,
                                totalPages = 1, numberOfElements = books.size, empty = books.isEmpty())
                        }
                        "getOne" -> checkNotNull(availableBook)
                        else -> error("Unexpected book request $it")
                    }
                },
                libraryApi = stub { checkNotNull(availableBook); library() },
                events = events,
                notifications = notifications,
                libraries = MutableStateFlow(emptyList()),
                taskEmitter = null,
                localLibraryManager = null,
                settingsRepository = stub { flowOf(240) },
                readListApi = stub { error("No book is selected") },
                collectionApi = stub { error("No collection is selected") },
            )
        }
        try {
            model.initialize()
            advanceUntilIdle()
            if (failInitially) {
                assertIs<LoadState.Error>(model.state.value)
                assertEquals(1, notifications.getNotifications().first().size)
                notifications.getNotifications().first().forEach { notifications.remove(it.id) }
            } else {
                assertIs<LoadState.Success<Unit>>(model.state.value)
            }
            shouldFail = false
            model.reload()
            advanceUntilIdle()
            assertIs<LoadState.Success<Unit>>(model.state.value)
            assertNull(model.book.value)
            assertEquals(2, lookups)
            assertTrue(notifications.getNotifications().first().isEmpty())
            events.emit(KomgaEvent.SeriesChanged(series.id, series.libraryId))
            advanceUntilIdle()
            assertIs<LoadState.Success<Unit>>(model.state.value)
            assertEquals(3, lookups)
            assertTrue(notifications.getNotifications().first().isEmpty())
            availableBook = book()
            model.reload()
            advanceUntilIdle()
            assertIs<LoadState.Success<Unit>>(model.state.value)
            assertEquals(availableBook, model.book.value)
            assertEquals(library().id, model.library.value?.id)
            assertEquals(4, lookups)
            assertTrue(notifications.getNotifications().first().isEmpty())
        } finally {
            ScreenModelStore.onDisposeNavigator(owner)
            Dispatchers.resetMain()
        }
    }

    private fun book(): KomeliaBook {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(
            id = KomgaBookId("local-book-test"), seriesId = KomgaSeriesId("local-series-empty"),
            seriesTitle = "Synthetic series", libraryId = KomgaLibraryId("local-library-test"),
            name = "Synthetic book", url = "local://synthetic", number = 1,
            created = now, lastModified = now, fileLastModified = now, sizeBytes = 20, size = "20 B",
            media = Media(KomgaMediaStatus.READY, "application/zip", 3, "", false, false, MediaProfile.DIVINA),
            metadata = KomgaBookMetadata("Synthetic book", "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, now, now),
            readProgress = null, deleted = false, fileHash = "", oneshot = true, downloaded = true,
            localFileLastModified = now, remoteFileUnavailable = false,
        )
    }

    private fun library(): KomgaLibrary = KomgaLibrary(
        id = KomgaLibraryId("local-library-test"), name = "Synthetic library", root = "local://synthetic",
        importComicInfoBook = false, importComicInfoSeries = false, importComicInfoCollection = false,
        importComicInfoReadList = false, importComicInfoSeriesAppendVolume = false,
        importEpubBook = false, importEpubSeries = false, importMylarSeries = false,
        importLocalArtwork = false, importBarcodeIsbn = false, scanForceModifiedTime = false,
        scanInterval = ScanInterval.DISABLED, scanOnStartup = false, scanCbx = true, scanPdf = true, scanEpub = true,
        scanDirectoryExclusions = emptyList(), repairExtensions = false, convertToCbz = false,
        emptyTrashAfterScan = false, seriesCover = SeriesCover.FIRST,
        hashFiles = false, hashPages = false, hashKoreader = false, analyzeDimensions = false,
        oneshotsDirectory = null, unavailable = false,
    )

    private fun series(): KomgaSeries {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomgaSeries(
            id = KomgaSeriesId("local-series-empty"),
            libraryId = KomgaLibraryId("local-library-test"),
            name = "Synthetic series",
            url = "local://synthetic",
            booksCount = 1,
            booksReadCount = 0,
            booksUnreadCount = 1,
            booksInProgressCount = 0,
            metadata = KomgaSeriesMetadata(
                status = KomgaSeriesStatus.ONGOING, statusLock = false,
                title = "Synthetic series", titleLock = false,
                alternateTitles = emptyList(), alternateTitlesLock = false,
                titleSort = "Synthetic series", titleSortLock = false,
                summary = "", summaryLock = false,
                readingDirection = null, readingDirectionLock = false,
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
            oneshot = true,
            booksMetadata = KomgaSeriesBookMetadata(emptyList(), emptyList(), null, "", "", now, now),
            created = now,
            lastModified = now,
            fileLastModified = now,
        )
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as T
}
