package snd.komelia.ui.settings.offline.logs

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.offline.sync.model.LogEntryId
import snd.komelia.offline.sync.model.OfflineLogEntry
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.settings.offline.OfflineOperationLogger
import snd.komga.client.common.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineLogsStateTest {
    @Test
    fun repositoryFailureIsAnErrorStateInsteadOfAnEmptyLog() = runTest {
        val state = OfflineLogsState(FakeLogRepository(failReads = true), this)

        state.initialize()

        assertIs<LoadState.Error>(state.loadState.value)
        assertEquals(emptyList(), state.logs.value)
    }

    @Test
    fun operationLoggerPersistsATypeSafeErrorEntry() = runTest {
        val repository = FakeLogRepository()
        val logger = OfflineOperationLogger(repository, this)

        logger.record(OfflineLogEntry.Operation.USER_SWITCH, IllegalStateException("secret"))
        runCurrent()

        assertEquals(1, repository.saved.size)
        assertEquals(OfflineLogEntry.Type.ERROR, repository.saved.single().type)
        assertEquals("USER_SWITCH: IllegalStateException", repository.saved.single().message)
    }

    @Test
    fun tabChangesReloadTheSelectedLogType() = runTest {
        val repository = FakeLogRepository()
        val state = OfflineLogsState(repository, this)

        state.initialize()
        state.onTabChange(OfflineLogsState.TaskTab.INFO)
        runCurrent()

        assertEquals(
            listOf(OfflineLogEntry.Type.ERROR, OfflineLogEntry.Type.INFO),
            repository.requestedTypes,
        )
        assertIs<LoadState.Success<Unit>>(state.loadState.value)
    }

    @Test
    fun deleteResetsPaginationAndReloadsTheCurrentTab() = runTest {
        val repository = FakeLogRepository()
        val state = OfflineLogsState(repository, this)
        state.initialize()
        state.onPageChange(3)
        runCurrent()

        state.onLogsDelete()
        runCurrent()

        assertEquals(1, repository.deleteCalls)
        assertEquals(1, state.pageNumber.value)
        assertEquals(OfflineLogEntry.Type.ERROR, repository.requestedTypes.last())
        assertIs<LoadState.Success<Unit>>(state.loadState.value)
    }

    private class FakeLogRepository(
        private val failReads: Boolean = false,
    ) : LogJournalRepository {
        val saved = mutableListOf<OfflineLogEntry>()
        val requestedTypes = mutableListOf<OfflineLogEntry.Type>()
        var deleteCalls = 0

        override suspend fun save(entry: OfflineLogEntry) {
            saved += entry
        }

        override suspend fun get(id: LogEntryId): OfflineLogEntry = error("unused")

        override suspend fun findAll(limit: Int, offset: Long): Page<OfflineLogEntry> = error("unused")

        override suspend fun findAllByType(
            type: OfflineLogEntry.Type,
            limit: Int,
            offset: Long,
        ): Page<OfflineLogEntry> {
            if (failReads) error("database unavailable")
            requestedTypes += type
            return Page.empty()
        }

        override suspend fun deleteAll() {
            deleteCalls++
        }
    }
}
