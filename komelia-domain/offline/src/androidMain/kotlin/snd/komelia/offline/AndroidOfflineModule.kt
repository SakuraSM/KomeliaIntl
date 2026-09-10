package snd.komelia.offline

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.offline.mediacontainer.AndroidPdfExtractor
import snd.komelia.offline.mediacontainer.DivinaExtractor
import snd.komelia.offline.mediacontainer.EpubExtractor
import snd.komelia.offline.mediacontainer.EpubZipExtractor
import snd.komelia.offline.mediacontainer.PdfExtractor
import snd.komelia.offline.mediacontainer.ZipExtractor
import snd.komelia.offline.mediacontainer.AndroidArchiveSourceAccess
import snd.komelia.offline.mediacontainer.ComicArchiveExtractor
import snd.komelia.offline.mediacontainer.ComicArchiveService
import java.io.File
import snd.komelia.offline.sync.AndroidDownloadManager
import snd.komelia.offline.sync.BookDownloadService
import snd.komelia.offline.sync.PlatformDownloadManager
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.offline.tasks.repository.OfflineTasksRepository
import snd.komga.client.KomgaClientFactory
import snd.komga.client.user.KomgaUser

class AndroidOfflineModule(
    repositories: OfflineRepositories,
    onlineUser: StateFlow<KomgaUser?>,
    onlineServerUrl: StateFlow<String>,
    isOffline: StateFlow<Boolean>,
    komgaClientFactory: KomgaClientFactory,
    private val context: Context,
) : OfflineModule(
    repositories = repositories,
    authenticatedUser = onlineUser,
    onlineServerUrl = onlineServerUrl,
    isOffline = isOffline,
    komgaClientFactory = komgaClientFactory,
) {
    private val zipExtractor = ZipExtractor()

    override fun createDivinaExtractors(): List<DivinaExtractor> {
        val service = ComicArchiveService.forDirectory(File(context.cacheDir, "komelia-saf-archives"))
        val access = AndroidArchiveSourceAccess(context)
        return listOf(ComicArchiveExtractor(service) { access.source(it) })
    }

    override fun createEpubExtractor(): EpubExtractor {
        return EpubZipExtractor(zipExtractor)
    }

    override fun createPdfExtractor(): PdfExtractor {
        return AndroidPdfExtractor(context)
    }

    override fun createPlatformDownloadManager(
        downloadService: BookDownloadService,
        logJournalRepository: LogJournalRepository,
        events: MutableSharedFlow<DownloadEvent>,
        tasksRepository: OfflineTasksRepository,
    ): PlatformDownloadManager {
        return AndroidDownloadManager(
            context = context,
        )
    }
}
