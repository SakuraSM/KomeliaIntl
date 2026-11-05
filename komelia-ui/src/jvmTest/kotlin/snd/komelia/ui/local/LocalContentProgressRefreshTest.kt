package snd.komelia.ui.local

import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.OfflineRepositories
import snd.komelia.offline.local.AvailableBooksRepository
import snd.komga.client.book.*
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class LocalContentProgressRefreshTest {
    @Test
    fun returningToLocalContentReloadsProgressAndKeepsTheCurrentPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = book()
        var saved = snapshot
        var loads = 0
        val repositories = OfflineRepositories(
            mediaServerRepository = stub(), mediaRepository = stub(), bookRepository = stub(),
            bookMetadataRepository = stub(), bookMetadataAggregationRepository = stub(),
            libraryRepository = stub(), readProgressRepository = stub(), seriesMetadataRepository = stub(),
            seriesRepository = stub(), thumbnailBookRepository = stub(), thumbnailSeriesRepository = stub(),
            userRepository = stub(), bookDtoRepository = stub { name, args ->
                check(name == "findAll")
                loads++
                val request = args.filterIsInstance<KomgaPageRequest>().single()
                Page.empty<KomeliaBook>().copy(
                    content = listOf(saved), number = request.pageIndex ?: 0,
                    totalElements = 40, totalPages = 2, numberOfElements = 1, empty = false,
                )
            },
            referentialRepository = stub(), seriesDtoRepository = stub(), logJournalRepository = stub(),
            transactionTemplate = stub(), tasksRepository = stub(), offlineSettingsRepository = stub(),
        )
        val holder = "local-progress-${java.util.UUID.randomUUID()}"
        val vm = ScreenModelStore.getOrPut(holder, null) {
            LocalContentViewModel(
                localLibraryManager = null, availableBooksRepository = AvailableBooksRepository(repositories),
                bookApi = stub(), appNotifications = AppNotifications(), taskEmitter = null,
                cardWidthFlow = flowOf(240.dp),
            )
        }
        try {
            vm.initialize()
            advanceUntilIdle()
            vm.onPageChange(2)
            advanceUntilIdle()
            assertEquals(2, vm.currentPage.value)
            assertEquals(1, vm.books.value.single().readProgress!!.page)

            saved = snapshot.copy(readProgress = snapshot.readProgress!!.copy(page = 2))
            vm.initialize()
            advanceUntilIdle()
            assertEquals(2, vm.currentPage.value)
            assertEquals(2, vm.books.value.single().readProgress!!.page)

            val previousLoads = loads
            vm.query.value = "other"
            advanceUntilIdle()
            assertEquals(previousLoads + 1, loads, "re-entry must not duplicate filter listeners")
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private fun book(): KomeliaBook {
        val now = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(
            id = KomgaBookId("book"), seriesId = KomgaSeriesId("series"), seriesTitle = "Synthetic",
            libraryId = KomgaLibraryId("library"), name = "Synthetic", url = "synthetic://book", number = 1,
            created = now, lastModified = now, fileLastModified = now, sizeBytes = 20, size = "20 B",
            media = Media(KomgaMediaStatus.READY, "application/zip", 3, "", false, false, MediaProfile.DIVINA),
            metadata = KomgaBookMetadata("Synthetic", "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, now, now),
            readProgress = ReadProgress(1, false, now, "test", "test", now, now),
            deleted = false, fileHash = "", oneshot = false, downloaded = true,
            localFileLastModified = now, remoteFileUnavailable = false,
        )
    }

    private inline fun <reified T> stub(crossinline answer: (String, List<Any?>) -> Any? = { name, _ -> error("Unexpected call $name") }): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            answer(method.name.substringBefore('-'), args?.toList().orEmpty())
        } as T
}
