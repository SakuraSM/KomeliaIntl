package snd.komelia.offline.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class LocalLibraryScanWorker(
    context: Context,
    workerParameters: WorkerParameters,
    private val manager: LocalLibraryManager,
) : CoroutineWorker(context, workerParameters) {
    override suspend fun doWork(): Result = try {
        manager.scanScheduled()
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
