package snd.komelia.offline.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.io.Sink
import kotlinx.io.files.Path
import snd.komelia.offline.book.actions.BookKomgaImportAction
import snd.komelia.offline.library.actions.LibraryKomgaImportAction
import snd.komelia.offline.series.actions.SeriesKomgaImportAction
import snd.komelia.offline.server.actions.MediaServerSaveAction
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.sync.model.DownloadEvent.BookDownloadProgress
import snd.komelia.offline.user.actions.UserKomgaImportAction
import snd.komga.client.book.KomgaBook
import snd.komga.client.book.KomgaBookClient
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryClient
import snd.komga.client.series.KomgaSeries
import snd.komga.client.series.KomgaSeriesClient
import snd.komga.client.user.KomgaUserClient


private val logger = KotlinLogging.logger { }
private const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024

class BookDownloadService(
    private val libraryDownloadPath: Flow<PlatformFile>,

    private val bookClient: KomgaBookClient,
    private val downloadClient: HttpClient,
    private val seriesClient: KomgaSeriesClient,
    private val libraryClient: KomgaLibraryClient,
    private val userClient: KomgaUserClient,

    private val saveUserAction: UserKomgaImportAction,
    private val saveServerAction: MediaServerSaveAction,
    private val libraryImportAction: LibraryKomgaImportAction,
    private val seriesImportAction: SeriesKomgaImportAction,
    private val bookImportAction: BookKomgaImportAction,

    private val onlineServerUrl: StateFlow<String>,
) {

    fun downloadBook(bookId: KomgaBookId): Flow<DownloadEvent> {
        return flow {
            var file: PlatformFile? = null
            val book = bookClient.getOne(bookId)
            try {
                val user = userClient.getMe()
                val serverUrl = onlineServerUrl.value
                val library = libraryClient.getLibrary(book.libraryId)
                val series = seriesClient.getOneSeries(book.seriesId)

                val bookFile = doDownload(library, series, book).also { file = it }

                val offlineServer = saveServerAction.execute(serverUrl)
                libraryImportAction.execute(library, offlineServer.id)
                seriesImportAction.execute(series)
                val offlineUser = saveUserAction.execute(user, offlineServer.id)
                bookImportAction.execute(
                    book = book,
                    offlinePath = bookFile,
                    userId = offlineUser.id,
                    localFileModifiedDate = book.fileLastModified
                )
            } catch (e: Exception) {
                file?.let { deleteFile(it) }
                currentCoroutineContext().ensureActive()
                logger.catching(e)
                emit(
                    DownloadEvent.BookDownloadError(
                        bookId = bookId,
                        error = e,
                        book = book
                    )
                )
            }
        }
    }

    private suspend fun FlowCollector<DownloadEvent>.doDownload(
        library: KomgaLibrary,
        series: KomgaSeries,
        book: KomgaBook,
    ): PlatformFile {
        val url = URLBuilder(onlineServerUrl.value).build()
        val (file, output) = prepareOutput(
            downloadRoot = libraryDownloadPath.first(),
            serverName = buildString {
                append(url.host)
                if (url.specifiedPort != 0) append("_${url.specifiedPort}")
                url.segments.forEach { append("_$it") }
            },
            libraryName = library.name,
            seriesName = series.name,
            bookFileName = Path(book.url).name,
        )

        try {
            val expectedSize = book.sizeBytes.takeIf { it > 0L }
            emit(BookDownloadProgress(book, expectedSize ?: 0L, 0))
            ResumableDownloadWriter(
                sourceFactory = KtorDownloadSourceFactory(downloadClient),
                onRetry = { attempt, offset, error ->
                    logger.warn(error) {
                        "book file download retry attempt=$attempt offset=$offset"
                    }
                }
            ).write(
                bookId = book.id,
                expectedSize = expectedSize,
                output = output,
            ) { completed, total ->
                emit(BookDownloadProgress(book, total ?: 0L, completed))
            }
        } catch (e: Exception) {
            deleteFile(file)
            throw e
        } finally {
            output.close()
        }

        val event = DownloadEvent.BookDownloadCompleted(book)
        emit(event)

        return file
    }
}

internal expect suspend fun prepareOutput(
    downloadRoot: PlatformFile,
    serverName: String,
    libraryName: String,
    seriesName: String,
    bookFileName: String,
): Pair<PlatformFile, Sink>

internal expect suspend fun deleteFile(file: PlatformFile)

private class KtorDownloadSourceFactory(
    private val client: HttpClient,
) : DownloadSourceFactory {
    override suspend fun open(bookId: KomgaBookId, offset: Long, endInclusive: Long): DownloadSource {
        val statement = client.prepareGet("api/v1/books/${bookId.value}/file") {
            accept(ContentType.Application.OctetStream)
            header(HttpHeaders.AcceptEncoding, "identity")
            header(HttpHeaders.CacheControl, "no-store")
            expectSuccess = false
            header(HttpHeaders.Range, downloadRangeHeader(offset, endInclusive))
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            }
        }
        return KtorDownloadSource(statement)
    }
}

internal fun downloadRangeHeader(offset: Long, endInclusive: Long): String {
    require(offset >= 0L) { "Download offset cannot be negative" }
    require(endInclusive >= offset) { "Download range end cannot precede its offset" }
    return "bytes=$offset-$endInclusive"
}

private class KtorDownloadSource(
    private val statement: HttpStatement,
) : DownloadSource {
    override suspend fun transferTo(
        output: Sink,
        onResponse: suspend (DownloadResponseMetadata) -> Boolean,
        onBytesWritten: suspend (Long) -> Unit,
    ): Long = statement.execute { response ->
        val metadata = DownloadResponseMetadata(
            statusCode = response.status.value,
            contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
            contentRange = response.headers[HttpHeaders.ContentRange],
        )
        logger.debug {
            "book file response status=${metadata.statusCode} " +
                "contentLength=${metadata.contentLength} contentRange=${metadata.contentRange}"
        }
        if (!onResponse(metadata)) return@execute 0L
        transferByteChannelToSink(
            channel = response.bodyAsChannel(),
            output = output,
            onBytesWritten = onBytesWritten,
        )
    }
}

internal suspend fun transferByteChannelToSink(
    channel: ByteReadChannel,
    output: Sink,
    onBytesWritten: suspend (Long) -> Unit,
): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var transferred = 0L
    while (true) {
        val bytesRead = channel.readAvailable(buffer)
        if (bytesRead == -1) return transferred
        if (bytesRead == 0) continue

        output.write(buffer, startIndex = 0, endIndex = bytesRead)
        transferred += bytesRead
        onBytesWritten(bytesRead.toLong())
    }
}
