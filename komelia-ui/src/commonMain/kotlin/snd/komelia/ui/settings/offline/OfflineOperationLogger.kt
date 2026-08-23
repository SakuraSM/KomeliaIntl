package snd.komelia.ui.settings.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import snd.komelia.offline.sync.model.OfflineLogEntry
import snd.komelia.offline.sync.repository.LogJournalRepository

internal class OfflineOperationLogger(
    private val repository: LogJournalRepository,
    private val coroutineScope: CoroutineScope,
) {
    fun record(operation: OfflineLogEntry.Operation, error: Throwable) {
        coroutineScope.launch {
            try {
                repository.save(OfflineLogEntry.operationError(operation, error))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Never replace the original user-visible failure with a logging failure.
            }
        }
    }
}
