package snd.komelia.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import snd.komelia.updates.OnnxModelDownloader.CompletionEvent
import snd.komelia.updates.OnnxModelDownloader.CompletionEvent.PanelModelDownloaded
import java.nio.file.Path

private const val panelDetectionModelLink =
    "https://github.com/Snd-R/komelia-onnxruntime/releases/download/model/rf-detr-nano.onnx.zip"

class AndroidOnnxModelDownloader(
    private val updateClient: UpdateClient,
    dataDir: Path,
) : OnnxModelDownloader {
    override val downloadCompletionEvents = MutableSharedFlow<CompletionEvent>()
    private val downloadLock = Mutex()
    private val panelModels = OnnxModelArchive(dataDir, setOf("rf-detr-nano.onnx"))

    override fun mangaJaNaiDownload(): Flow<UpdateProgress> = emptyFlow()

    override fun panelDownload(): Flow<UpdateProgress> = flow {
        downloadLock.withLock {
            downloadOnnxModel(updateClient, panelDetectionModelLink, panelModels)
            withContext(NonCancellable) { downloadCompletionEvents.emit(PanelModelDownloaded) }
        }
    }.flowOn(Dispatchers.IO)
}
