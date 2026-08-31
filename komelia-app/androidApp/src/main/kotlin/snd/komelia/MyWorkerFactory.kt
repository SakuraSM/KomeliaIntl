package snd.komelia

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
import snd.komelia.offline.OfflineDependencies
import snd.komelia.offline.local.LocalLibraryScanWorker
import snd.komelia.offline.sync.DownloadWorker
import snd.komelia.offline.tasks.DownloadTaskTracker

class MyWorkerFactory(
    private val dependenciesProvider: suspend (Context) -> OfflineDependencies?,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return runBlocking {
            val currentDependencies = dependenciesProvider(appContext) ?: return@runBlocking null

            when (workerClassName) {
                DownloadWorker::class.qualifiedName -> DownloadWorker(
                    context = appContext,
                    workerParams = workerParameters,
                    downloadService = currentDependencies.downloadService,
                    logsJournalRepository = currentDependencies.repositories.logJournalRepository,
                    sharedEvents = currentDependencies.bookDownloadEvents,
                    downloadTaskTracker = DownloadTaskTracker(currentDependencies.repositories.tasksRepository),
                )
                LocalLibraryScanWorker::class.qualifiedName -> currentDependencies.localLibraryManager?.let { manager ->
                    LocalLibraryScanWorker(appContext, workerParameters, manager)
                }
                else -> null
            }
        }
    }
}
