package snd.komelia.ui

import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.lang.reflect.Proxy
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import snd.komf.client.KomfMangaBakaClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.settings.model.BooksLayout
import snd.komelia.ui.series.SeriesBooksState
import snd.komelia.ui.series.SeriesViewModel
import snd.komelia.ui.readlist.ReadListViewModel
import snd.komga.client.book.ReadProgress
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.readlist.KomgaReadListId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import snd.komelia.komga.api.model.KomeliaBook
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
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komga.client.series.KomgaSeriesBookMetadata
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesMetadata
import snd.komga.client.series.KomgaSeriesStatus
import snd.komga.client.sse.KomgaEvent
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class CatalogProgressRefreshTest {
    @Test fun returningToSeriesRefreshesProgressWithoutAddingEventListeners() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var saved = book()
        val events = MutableSharedFlow<KomgaEvent>()
        val holder = "series-progress-${java.util.UUID.randomUUID()}"
        val series = series().copy(oneshot = false)
        val httpClient = HttpClient(OkHttp)
        val vm = ScreenModelStore.getOrPut(holder, null) { SeriesViewModel(
            series = series, seriesId = series.id, libraries = MutableStateFlow(listOf(library())),
            notifications = AppNotifications(), events = events, taskEmitter = null, localLibraryManager = null,
            libraryApi = stub { library() },
            seriesApi = stub { name -> when (name) {
                "getAllCollectionsBySeries" -> emptyList<Any>()
                "getOne" -> series
                else -> error("Unexpected series call $name")
            } },
            bookApi = stub { name -> when (name) {
                "getBookList" -> page(saved)
                else -> error("Unexpected book call $name")
            } },
            collectionApi = stub { error("No collections") },
            referentialApi = stub { name -> when (name) {
                "getBookTags" -> emptyList<String>()
                "getAuthors" -> Page.empty<Any>()
                else -> error("Unexpected referential call $name")
            } },
            settingsRepository = stub { name -> when (name) {
                "getCardWidth" -> flowOf(240)
                "getBookPageLoadSize" -> flowOf(20)
                "getBookListLayout" -> flowOf(BooksLayout.LIST)
                else -> error("Unexpected setting $name")
            } },
            komfMangaBakaClient = KomfMangaBakaClient(httpClient),
            komfSettingsRepository = stub { flowOf(false) }, defaultTab = SeriesViewModel.SeriesTab.BOOKS,
        ) }
        try {
            vm.initialize(); advanceUntilIdle()
            val subscriptions = events.subscriptionCount.value
            saved = saved.copy(readProgress = saved.readProgress!!.copy(page = 2))
            vm.initialize(); advanceUntilIdle()
            val data = assertIs<LoadState.Success<SeriesBooksState.BooksData>>(vm.booksState.state.value).value
            assertEquals(2, data.books.single().readProgress!!.page)
            assertEquals(subscriptions, events.subscriptionCount.value)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder); httpClient.close(); Dispatchers.resetMain()
        }
    }

    @Test fun returningToReadListRefreshesProgressWithoutAddingEventListeners() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var saved = book()
        val now = saved.created
        val list = KomgaReadList(KomgaReadListId("list"), "Synthetic list", "", true, listOf(saved.id), now, now, false)
        val events = MutableSharedFlow<KomgaEvent>()
        val holder = "read-list-progress-${java.util.UUID.randomUUID()}"
        val vm = ScreenModelStore.getOrPut(holder, null) { ReadListViewModel(
            readListId = list.id, readListApi = stub { name -> when (name) {
                "getOne" -> list
                "getBooksForReadList" -> page(saved)
                "updateOne" -> Unit
                else -> error("Unexpected read-list call $name")
            } }, bookApi = stub { error("No book mutation") }, taskEmitter = null,
            notifications = AppNotifications(), komgaEvents = events, cardWidthFlow = flowOf(240.dp),
        ) }
        try {
            vm.initialize(); advanceUntilIdle()
            val subscriptions = events.subscriptionCount.value
            saved = saved.copy(readProgress = saved.readProgress!!.copy(page = 2))
            vm.initialize(); advanceUntilIdle()
            assertEquals(2, vm.books.single().readProgress!!.page)
            assertEquals(subscriptions, events.subscriptionCount.value)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder); Dispatchers.resetMain()
        }
    }

    private fun page(book: KomeliaBook) = Page.empty<KomeliaBook>().copy(
        content = listOf(book), totalElements = 1, totalPages = 1, numberOfElements = 1, empty = false)

    private fun book(): KomeliaBook {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(
            id = KomgaBookId("local-book-test"), seriesId = KomgaSeriesId("local-series-empty"),
            seriesTitle = "Synthetic series", libraryId = KomgaLibraryId("local-library-test"),
            name = "Synthetic book", url = "local://synthetic", number = 1,
            created = now, lastModified = now, fileLastModified = now,
            sizeBytes = 20, size = "20 B",
            media = Media(KomgaMediaStatus.READY, "application/zip", 3, "", false, false, MediaProfile.DIVINA),
            metadata = KomgaBookMetadata("Synthetic book", "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, now, now),
            readProgress = ReadProgress(1, false, now, "test", "test", now, now), deleted = false, fileHash = "", oneshot = true, downloaded = true,
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

    private fun series(): KomeliaSeries {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaSeries(
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
            mangaBakaMetadata = null,
        )
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as T
}
