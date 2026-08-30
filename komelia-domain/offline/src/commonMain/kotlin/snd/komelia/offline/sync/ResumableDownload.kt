package snd.komelia.offline.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.Sink
import snd.komga.client.book.KomgaBookId

private const val DEFAULT_MAX_DOWNLOAD_ATTEMPTS = 5
private const val DEFAULT_DOWNLOAD_CHUNK_SIZE = 4L * 1024L * 1024L
private const val MAX_RETRY_DELAY_MILLIS = 4_000L
private val contentRangeRegex = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")

internal interface DownloadSource {
    suspend fun transferTo(
        output: Sink,
        onResponse: suspend (DownloadResponseMetadata) -> Boolean,
        onBytesWritten: suspend (Long) -> Unit,
    ): Long
}

internal data class DownloadResponseMetadata(
    val statusCode: Int,
    val contentLength: Long?,
    val contentRange: String?,
)

internal fun interface DownloadSourceFactory {
    suspend fun open(bookId: KomgaBookId, offset: Long, endInclusive: Long): DownloadSource
}

internal data class ParsedContentRange(
    val start: Long,
    val endInclusive: Long,
    val total: Long?,
)

internal class DownloadProtocolException(message: String) : IllegalStateException(message)

internal fun parseContentRange(value: String?): ParsedContentRange? {
    val match = value?.let(contentRangeRegex::matchEntire) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val endInclusive = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull()
    if (endInclusive < start) return null
    if (total != null && endInclusive >= total) return null
    return ParsedContentRange(start, endInclusive, total)
}

internal class ResumableDownloadWriter(
    private val sourceFactory: DownloadSourceFactory,
    private val maxAttempts: Int = DEFAULT_MAX_DOWNLOAD_ATTEMPTS,
    private val chunkSize: Long = DEFAULT_DOWNLOAD_CHUNK_SIZE,
    private val retryDelay: suspend (attempt: Int) -> Unit = { attempt ->
        delay((500L shl attempt).coerceAtMost(MAX_RETRY_DELAY_MILLIS))
    },
    private val onRetry: suspend (attempt: Int, offset: Long, error: Throwable) -> Unit = { _, _, _ -> },
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(chunkSize > 0L) { "chunkSize must be positive" }
    }

    suspend fun write(
        bookId: KomgaBookId,
        expectedSize: Long?,
        output: Sink,
        onProgress: suspend (completed: Long, total: Long?) -> Unit,
    ): Long {
        var completed = 0L
        var total = expectedSize?.takeIf { it > 0L }
        var attempt = 0

        while (true) {
            if (total != null && completed == total) return completed
            try {
                val requestedEnd = rangeEnd(completed, total, chunkSize)
                val source = sourceFactory.open(bookId, completed, requestedEnd)
                var responseMetadata: DownloadResponseMetadata? = null
                var parsedRange: ParsedContentRange? = null
                var alreadyComplete = false
                val responseStart = completed
                val transferred = source.transferTo(
                    output = output,
                    onResponse = { metadata ->
                        if (responseMetadata != null) {
                            throw DownloadProtocolException("Download source reported multiple responses")
                        }
                        responseMetadata = metadata
                        parsedRange = validateResponse(metadata, completed, requestedEnd, total)
                        val responseTotal = parsedRange?.total
                            ?: metadata.contentLength?.takeIf {
                                completed == 0L && metadata.statusCode == 200
                            }

                        if (responseTotal != null) {
                            if (total != null && total != responseTotal) {
                                throw DownloadProtocolException(
                                    "Download size changed from $total to $responseTotal bytes"
                                )
                            }
                            total = responseTotal
                        }

                        alreadyComplete = metadata.statusCode == 416 && total != null && completed == total
                        !alreadyComplete
                    },
                    onBytesWritten = { bytesWritten ->
                        if (bytesWritten <= 0L) {
                            throw DownloadProtocolException("Download source reported a non-positive write")
                        }
                        completed += bytesWritten
                        if (total != null && completed > total) {
                            throw DownloadProtocolException(
                                "Downloaded $completed bytes, expected at most $total"
                            )
                        }
                        onProgress(completed, total)
                    },
                )

                val metadata = responseMetadata
                    ?: throw DownloadProtocolException("Download source did not report a response")
                if (alreadyComplete) return completed

                if (transferred != completed - responseStart) {
                    throw DownloadProtocolException(
                        "Download source reported $transferred bytes but wrote ${completed - responseStart}"
                    )
                }
                if (metadata.contentLength != null && transferred != metadata.contentLength) {
                    throw IncompleteDownloadException(
                        "Response ended after $transferred of ${metadata.contentLength} bytes"
                    )
                }
                val rangeLength = parsedRange?.let { it.endInclusive - it.start + 1L }
                if (rangeLength != null && transferred != rangeLength) {
                    throw IncompleteDownloadException(
                        "Partial response ended after $transferred of $rangeLength bytes"
                    )
                }
                if (total != null && completed < total) {
                    if (parsedRange == null) {
                        throw IncompleteDownloadException(
                            "Download ended after $completed of $total bytes"
                        )
                    }
                    output.flush()
                    attempt = 0
                    continue
                }

                output.flush()
                return completed
            } catch (error: Throwable) {
                if (error is CancellationException || error is DownloadProtocolException) throw error
                if (attempt >= maxAttempts - 1) throw error

                output.flush()
                attempt += 1
                onRetry(attempt, completed, error)
                retryDelay(attempt - 1)
            }
        }
    }

    private fun validateResponse(
        response: DownloadResponseMetadata,
        offset: Long,
        requestedEnd: Long,
        expectedSize: Long?,
    ): ParsedContentRange? {
        if (response.statusCode == 416 && expectedSize != null && offset == expectedSize) return null
        if (response.statusCode == 408 || response.statusCode == 429 || response.statusCode >= 500) {
            throw IncompleteDownloadException("Download request failed with HTTP ${response.statusCode}")
        }
        if (response.statusCode !in setOf(200, 206)) {
            throw DownloadProtocolException("Download request failed with HTTP ${response.statusCode}")
        }

        val parsedRange = parseContentRange(response.contentRange)
        if (response.statusCode == 206) {
            if (parsedRange == null) {
                throw DownloadProtocolException("Partial response did not include a valid Content-Range")
            }
            if (parsedRange.start != offset) {
                throw DownloadProtocolException(
                    "Server resumed at byte ${parsedRange.start}, expected $offset"
                )
            }
            if (parsedRange.endInclusive > requestedEnd) {
                throw DownloadProtocolException(
                    "Server returned through byte ${parsedRange.endInclusive}, requested at most $requestedEnd"
                )
            }
        } else if (offset > 0L) {
            throw DownloadProtocolException(
                "Server or reverse proxy ignored the byte-range request at byte $offset " +
                    "(HTTP ${response.statusCode}); use a direct LAN address or enable Range forwarding"
            )
        }

        return parsedRange
    }
}

internal fun rangeEnd(offset: Long, total: Long?, chunkSize: Long): Long {
    require(offset >= 0L) { "Download offset cannot be negative" }
    require(chunkSize > 0L) { "chunkSize must be positive" }
    val chunkEnd = if (offset > Long.MAX_VALUE - (chunkSize - 1L)) {
        Long.MAX_VALUE
    } else {
        offset + chunkSize - 1L
    }
    return total?.let { minOf(chunkEnd, it - 1L) } ?: chunkEnd
}

private class IncompleteDownloadException(message: String) : IllegalStateException(message)
