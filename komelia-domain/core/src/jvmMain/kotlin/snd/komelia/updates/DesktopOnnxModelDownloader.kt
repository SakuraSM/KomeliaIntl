package snd.komelia.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import snd.komelia.AppDirectories
import snd.komelia.updates.OnnxModelDownloader.CompletionEvent
import snd.komelia.updates.OnnxModelDownloader.CompletionEvent.MangaJaNaiDownloaded
import snd.komelia.updates.OnnxModelDownloader.CompletionEvent.PanelModelDownloaded

private const val mangaJaNaiDownloadLink =
    "https://github.com/Snd-R/mangajanai/releases/download/1.0.0/MangaJaNaiOnnxModels.zip"
private const val panelDetectionModelLink =
    "https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/rf-detr-med.onnx.zip"

class DesktopOnnxModelDownloader(private val updateClient: UpdateClient) : OnnxModelDownloader {
    override val downloadCompletionEvents = MutableSharedFlow<CompletionEvent>()
    private val downloadLock = Mutex()

    override fun mangaJaNaiDownload(): Flow<UpdateProgress> = download(
        url = mangaJaNaiDownloadLink,
        models = OnnxModelArchive(
            AppDirectories.mangaJaNaiInstallPath,
            AppDirectories.mangaJaNaiModelFiles,
            AppDirectories.mangaJaNaiIllustrationModelFiles,
        ),
        completed = MangaJaNaiDownloaded,
    )

    override fun panelDownload(): Flow<UpdateProgress> = download(
        url = panelDetectionModelLink,
        models = OnnxModelArchive(
            AppDirectories.panelDetectionInstallPath,
            setOf(AppDirectories.panelDetectionModelPath.fileName.toString()),
        ),
        completed = PanelModelDownloaded,
    )

    private fun download(
        url: String,
        models: OnnxModelArchive,
        completed: CompletionEvent,
    ): Flow<UpdateProgress> = flow {
        downloadLock.withLock {
            downloadOnnxModel(updateClient, url, models)
            withContext(NonCancellable) { downloadCompletionEvents.emit(completed) }
        }
    }.flowOn(Dispatchers.IO)
}
