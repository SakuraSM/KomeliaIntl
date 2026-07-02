package snd.komelia.image

import coil3.disk.DiskCache
import io.ktor.client.plugins.ResponseException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import okio.FileSystem
import okio.Path.Companion.toPath
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId

private val logger = KotlinLogging.logger {}

class BookImageLoader(
    private val bookClient: StateFlow<KomgaBookApi>,
    private val imageDecoder: KomeliaImageDecoder,
    private val readerImageFactory: ReaderImageFactory,
    //TODO consider non coil disk cache implementation?
    val diskCache: DiskCache?,
) {
    val fileSystem = diskCache?.fileSystem

    suspend fun loadReaderImage(bookId: KomgaBookId, page: Int): ReaderImageResult {
        val maxAttempts = 3
        var lastError: Throwable? = null

        repeat(maxAttempts) { attemptIndex ->
            val attempt = attemptIndex + 1
            try {
                val source = doLoad(bookId, page)
                if (attempt > 1) {
                    logger.info { "reader image load recovered bookId=$bookId page=$page attempt=$attempt" }
                }
                return ReaderImageResult.Success(readerImageFactory.getImage(source, ReaderImage.PageId(bookId.value, page)))
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                lastError = e

                if (!e.isRetryableReaderImageError() || attempt == maxAttempts) {
                    logger.catching(e)
                    logger.warn {
                        "reader image load failed bookId=$bookId page=$page attempt=$attempt " +
                            "status=${e.httpStatusCode()} exception=${e::class.simpleName}"
                    }
                    return ReaderImageResult.Error(e)
                }

                logger.warn {
                    "reader image load retry bookId=$bookId page=$page attempt=$attempt " +
                        "status=${e.httpStatusCode()} exception=${e::class.simpleName}"
                }
                delay(250L * attempt)
            }
        }

        return ReaderImageResult.Error(checkNotNull(lastError))
    }

    private fun Throwable.isRetryableReaderImageError(): Boolean {
        if (this is ResponseException) return response.status.value >= 500
        return try {
            this::class.simpleName?.contains("Cancellation") != true
        } catch (_: Throwable) {
            true
        }
    }

    private fun Throwable.httpStatusCode(): Int? = (this as? ResponseException)?.response?.status?.value

    // TODO remove
    suspend fun loadImage(bookId: KomgaBookId, page: Int): ImageResult {
        return try {
            doLoad(bookId, page).use { source ->
                val image = when (source) {
                    is ImageSource.FilePathSource -> {
                        val fileSystem = checkNotNull(fileSystem)
                        imageDecoder.decode(fileSystem.read(source.path.toPath()) { readByteArray() })
                    }

                    is ImageSource.MemorySource -> imageDecoder.decode(source.data)
                }
                ImageResult.Success(image)
            }
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.catching(e)
            ImageResult.Error(e)
        }
    }

    private suspend fun doLoad(bookId: KomgaBookId, page: Int): ImageSource {
        val pageId = ReaderImage.PageId(bookId.value, page)
        if (diskCache == null) {
            val bytes: ByteArray = bookClient.value.getPage(bookId, page)
            return ImageSource.MemorySource(bytes)
        }

        val existingSnapshot = diskCache.openSnapshot(pageId.toString())
        val fileSystem = diskCache.fileSystem
        if (existingSnapshot != null) {
            return ImageSource.FilePathSource(existingSnapshot)
        }

        val bytes = bookClient.value.getPage(bookId, page)
        val newSnapshot = writeToDiskCache(
            fileSystem = fileSystem,
            cacheKey = pageId.toString(),
            bytes = bytes
        )

        return newSnapshot?.let { ImageSource.FilePathSource(it) }
            ?: ImageSource.MemorySource(bytes)
    }

    private fun writeToDiskCache(
        fileSystem: FileSystem,
        cacheKey: String,
        bytes: ByteArray,
    ): DiskCache.Snapshot? {
        val editor = diskCache?.openEditor(cacheKey) ?: return null
        try {
            fileSystem.write(editor.data) { this.write(bytes) }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            editor.abort()
            throw e
        }
    }
}

sealed interface ReaderImageResult {
    val image: ReaderImage?

    data class Success(override val image: ReaderImage) : ReaderImageResult
    data class Error(val throwable: Throwable) : ReaderImageResult {
        override val image: ReaderImage? = null
    }
}

sealed interface ImageResult {
    val image: KomeliaImage?

    data class Success(override val image: KomeliaImage) : ImageResult
    data class Error(val throwable: Throwable) : ImageResult {
        override val image: KomeliaImage? = null
    }
}

sealed interface ImageSource : AutoCloseable {
    class MemorySource(val data: ByteArray) : ImageSource {
        override fun close() = Unit
    }

    class FilePathSource(
        val path: String,
        private val cacheLock: DiskCache.Snapshot?
    ) : ImageSource {
        constructor(snapshot: DiskCache.Snapshot) : this(path = snapshot.data.toString(), cacheLock = snapshot)

        override fun close() {
            cacheLock?.close()
        }
    }
}
