package snd.komelia.ui.local

import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.OfflineRepositories
import snd.komelia.offline.api.repository.OfflineBookDtoRepository
import snd.komelia.offline.local.AvailableBooksRepository
import snd.komelia.ui.LoadState
import snd.komga.client.book.*
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.user.KomgaUserId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class LocalContentProgressRefreshTest {
    @Test
    fun returningToLocalContentReloadsProgressAndKeepsTheCurrentPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val snapshot = book()
        var saved = snapshot
        var loads = 0
        val repositories = repositories(stub { name, args ->
            check(name == "findAll")
            loads++
            val request = args.filterIsInstance<KomgaPageRequest>().single()
            page(saved).copy(number = request.pageIndex ?: 0, totalElements = 40, totalPages = 2)
        })
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

    @Test fun lateReloadCannotReplaceNewSearch() = verifyLateReload(fails = false)
    @Test fun lateReloadFailureCannotReplaceNewSearch() = verifyLateReload(fails = true)

    private fun verifyLateReload(fails: Boolean) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val slow = CompletableDeferred<Page<KomeliaBook>>()
        val started = CompletableDeferred<Unit>()
        val notifications = AppNotifications()
        var first = true
        val dto = object : OfflineBookDtoRepository by stub() {
            override suspend fun findAll(userId: KomgaUserId, search: KomgaBookSearch, pageRequest: KomgaPageRequest): Page<KomeliaBook> =
                when (search.fullTextSearch) {
                    null -> if (first) {
                        first = false
                        page(book().copy(name = "initial"))
                    } else {
                        started.complete(Unit)
                        slow.await()
                    }
                    "needle" -> page(book().copy(name = "matching"))
                    else -> error("Unexpected search")
                }
        }
        val holder = "local-order-${java.util.UUID.randomUUID()}"
        val vm = model(holder, dto, notifications)
        try {
            vm.initialize()
            advanceUntilIdle()
            vm.reload()
            runCurrent()
            assertTrue(started.isCompleted)
            vm.query.value = "needle"
            advanceTimeBy(351)
            runCurrent()
            assertEquals("matching", vm.books.value.single().name)
            if (fails) slow.completeExceptionally(IllegalStateException("Old request failed"))
            else slow.complete(page(book().copy(name = "outdated")))
            advanceUntilIdle()
            assertEquals("matching", vm.books.value.single().name)
            assertEquals(0, notifications.getNotifications().first().size)
        } finally {
            slow.cancel()
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    @Test fun newerSearchDoesNotWaitForAnOlderSearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val slow = CompletableDeferred<Page<KomeliaBook>>()
        val started = CompletableDeferred<Unit>()
        val dto = object : OfflineBookDtoRepository by stub() {
            override suspend fun findAll(userId: KomgaUserId, search: KomgaBookSearch, pageRequest: KomgaPageRequest): Page<KomeliaBook> =
                when (search.fullTextSearch) {
                    null -> page(book().copy(name = "initial"))
                    "old" -> { started.complete(Unit); withContext(NonCancellable) { slow.await() } }
                    "needle" -> page(book().copy(name = "matching"))
                    else -> error("Unexpected search")
                }
        }
        val holder = "local-search-${java.util.UUID.randomUUID()}"
        val vm = model(holder, dto)
        try {
            vm.initialize()
            advanceUntilIdle()
            vm.query.value = "old"
            advanceTimeBy(351)
            runCurrent()
            assertTrue(started.isCompleted)
            vm.query.value = "needle"
            advanceTimeBy(351)
            runCurrent()
            assertEquals("matching", vm.books.value.single().name)
        } finally {
            slow.cancel()
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    @Test fun searchEnteredDuringInitialLoadIsNotLost() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = CompletableDeferred<Page<KomeliaBook>>()
        val started = CompletableDeferred<Unit>()
        val dto = object : OfflineBookDtoRepository by stub() {
            override suspend fun findAll(userId: KomgaUserId, search: KomgaBookSearch, pageRequest: KomgaPageRequest): Page<KomeliaBook> =
                when (search.fullTextSearch) {
                    null -> { started.complete(Unit); initial.await() }
                    "needle" -> page(book().copy(name = "matching"))
                    else -> error("Unexpected search")
                }
        }
        val holder = "local-initial-${java.util.UUID.randomUUID()}"
        val vm = model(holder, dto)
        try {
            val initialization = launch { vm.initialize() }
            runCurrent()
            assertTrue(started.isCompleted)
            vm.query.value = "needle"
            advanceTimeBy(351)
            runCurrent()
            assertEquals("matching", vm.books.value.single().name)
            initial.complete(page(book().copy(name = "outdated")))
            initialization.join()
            advanceUntilIdle()
            assertEquals("matching", vm.books.value.single().name)
        } finally {
            initial.cancel()
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    @Test fun currentFailureShowsAnErrorAndNotification() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val failure = IllegalStateException("Current load failed")
        val dto = object : OfflineBookDtoRepository by stub() {
            override suspend fun findAll(userId: KomgaUserId, search: KomgaBookSearch, pageRequest: KomgaPageRequest): Page<KomeliaBook> = throw failure
        }
        val notifications = AppNotifications()
        val holder = "local-error-${java.util.UUID.randomUUID()}"
        val vm = model(holder, dto, notifications)
        try {
            vm.initialize()
            advanceUntilIdle()
            assertEquals(failure, assertIs<LoadState.Error>(vm.state.value).exception)
            assertEquals(1, notifications.getNotifications().first().size)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    @Test fun cancelledInitialLoadKeepsSearchWorkingAfterReentry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val initial = CompletableDeferred<Page<KomeliaBook>>()
        val started = CompletableDeferred<Unit>()
        var first = true
        val dto = object : OfflineBookDtoRepository by stub() {
            override suspend fun findAll(userId: KomgaUserId, search: KomgaBookSearch, pageRequest: KomgaPageRequest): Page<KomeliaBook> =
                when (search.fullTextSearch) {
                    null -> if (first) {
                        first = false
                        started.complete(Unit)
                        initial.await()
                    } else page(book().copy(name = "reloaded"))
                    "needle" -> page(book().copy(name = "matching"))
                    else -> error("Unexpected search")
                }
        }
        val notifications = AppNotifications()
        val holder = "local-cancel-${java.util.UUID.randomUUID()}"
        val vm = model(holder, dto, notifications)
        try {
            val initialization = launch { vm.initialize() }
            runCurrent()
            assertTrue(started.isCompleted)
            initialization.cancel()
            runCurrent()
            vm.initialize()
            advanceUntilIdle()
            assertEquals("reloaded", vm.books.value.single().name)
            vm.query.value = "needle"
            advanceTimeBy(351)
            runCurrent()
            assertEquals("matching", vm.books.value.single().name)
            assertEquals(0, notifications.getNotifications().first().size)
        } finally {
            initial.cancel()
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private fun model(holder: String, dto: OfflineBookDtoRepository, notifications: AppNotifications = AppNotifications()) =
        ScreenModelStore.getOrPut(holder, null) {
            LocalContentViewModel(
                localLibraryManager = null, availableBooksRepository = AvailableBooksRepository(repositories(dto)),
                bookApi = stub(), appNotifications = notifications, taskEmitter = null,
                cardWidthFlow = flowOf(240.dp),
            )
        }

    private fun repositories(dto: OfflineBookDtoRepository) = OfflineRepositories(
        mediaServerRepository = stub(), mediaRepository = stub(), bookRepository = stub(),
        bookMetadataRepository = stub(), bookMetadataAggregationRepository = stub(),
        libraryRepository = stub(), readProgressRepository = stub(), seriesMetadataRepository = stub(),
        seriesRepository = stub(), thumbnailBookRepository = stub(), thumbnailSeriesRepository = stub(),
        userRepository = stub(), bookDtoRepository = dto, referentialRepository = stub(),
        seriesDtoRepository = stub(), logJournalRepository = stub(), transactionTemplate = stub(),
        tasksRepository = stub(), offlineSettingsRepository = stub(),
    )

    private fun page(book: KomeliaBook) = Page.empty<KomeliaBook>().copy(
        content = listOf(book), totalElements = 1, totalPages = 1, numberOfElements = 1, empty = false,
    )

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
