package snd.komelia.offline.sync

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ClosedByteChannelException
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResumableDownloadWriterTest {
    private val bookId = KomgaBookId("book")

    @Test
    fun `download resumes from the last written byte after a stream failure`() = runBlocking {
        val offsets = mutableListOf<Long>()
        val retries = mutableListOf<Pair<Int, Long>>()
        val sources = ArrayDeque(
            listOf(
                FakeDownloadSource(
                    statusCode = 200,
                    contentLength = 8,
                    bytes = "abcd".encodeToByteArray(),
                    failure = IllegalStateException("stream reset"),
                ),
                FakeDownloadSource(
                    statusCode = 206,
                    contentLength = 4,
                    contentRange = "bytes 4-7/8",
                    bytes = "efgh".encodeToByteArray(),
                ),
            )
        )
        val output = Buffer()

        val completed = ResumableDownloadWriter(
            sourceFactory = DownloadSourceFactory { _, offset, _ ->
                offsets += offset
                sources.removeFirst()
            },
            retryDelay = {},
            onRetry = { attempt, offset, _ -> retries += attempt to offset },
        ).write(bookId, expectedSize = 8, output = output) { _, _ -> }

        assertEquals(8, completed)
        assertEquals("abcdefgh", output.readByteArray().decodeToString())
        assertEquals(listOf(0L, 4L), offsets)
        assertEquals(listOf(1 to 4L), retries)
    }

    @Test
    fun `server must honor a range request before bytes are appended`() = runBlocking {
        val sources = ArrayDeque(
            listOf(
                FakeDownloadSource(
                    statusCode = 200,
                    contentLength = 8,
                    bytes = "abcd".encodeToByteArray(),
                    failure = IllegalStateException("stream reset"),
                ),
                FakeDownloadSource(
                    statusCode = 200,
                    contentLength = 4,
                    bytes = "efgh".encodeToByteArray(),
                ),
            )
        )
        val output = Buffer()
        val writer = ResumableDownloadWriter(
            sourceFactory = DownloadSourceFactory { _, _, _ -> sources.removeFirst() },
            retryDelay = {},
        )

        val error = assertFailsWith<DownloadProtocolException> {
            writer.write(bookId, expectedSize = 8, output = output) { _, _ -> }
        }

        assertEquals(
            "Server or reverse proxy ignored the byte-range request at byte 4 " +
                "(HTTP 200); use a direct LAN address or enable Range forwarding",
            error.message,
        )
        assertEquals("abcd", output.readByteArray().decodeToString())
    }

    @Test
    fun `retryable http status is retried without advancing the offset`() = runBlocking {
        val offsets = mutableListOf<Long>()
        val sources = ArrayDeque(
            listOf(
                FakeDownloadSource(statusCode = 503),
                FakeDownloadSource(
                    statusCode = 200,
                    contentLength = 4,
                    bytes = "done".encodeToByteArray(),
                ),
            )
        )
        val output = Buffer()

        ResumableDownloadWriter(
            sourceFactory = DownloadSourceFactory { _, offset, _ ->
                offsets += offset
                sources.removeFirst()
            },
            retryDelay = {},
        ).write(bookId, expectedSize = 4, output = output) { _, _ -> }

        assertEquals(listOf(0L, 0L), offsets)
        assertEquals("done", output.readByteArray().decodeToString())
    }

    @Test
    fun `non-retryable client status fails once`() = runBlocking {
        var requests = 0
        val writer = ResumableDownloadWriter(
            sourceFactory = DownloadSourceFactory { _, _, _ ->
                requests += 1
                FakeDownloadSource(statusCode = 406)
            },
            retryDelay = {},
        )

        val error = assertFailsWith<DownloadProtocolException> {
            writer.write(bookId, expectedSize = 4, output = Buffer()) { _, _ -> }
        }

        assertEquals("Download request failed with HTTP 406", error.message)
        assertEquals(1, requests)
    }

    @Test
    fun `cancellation is never retried`() = runBlocking {
        var requests = 0
        val writer = ResumableDownloadWriter(
            sourceFactory = DownloadSourceFactory { _, _, _ ->
                requests += 1
                throw CancellationException("cancelled")
            },
            retryDelay = {},
        )

        assertFailsWith<CancellationException> {
            writer.write(bookId, expectedSize = 4, output = Buffer()) { _, _ -> }
        }
        assertEquals(1, requests)
    }

    @Test
    fun `content range parsing rejects malformed or impossible ranges`() {
        assertEquals(
            ParsedContentRange(start = 4, endInclusive = 7, total = 8),
            parseContentRange("bytes 4-7/8")
        )
        assertEquals(
            ParsedContentRange(start = 4, endInclusive = 7, total = null),
            parseContentRange("bytes 4-7/*")
        )
        assertNull(parseContentRange("bytes 8-7/9"))
        assertNull(parseContentRange("bytes 4-8/8"))
        assertNull(parseContentRange("not-a-range"))
    }

    @Test
    fun `range request starts at zero and resumes at the exact completed byte`() {
        assertEquals("bytes=0-4194303", downloadRangeHeader(0, 4_194_303))
        assertEquals("bytes=65536-131071", downloadRangeHeader(65_536, 131_071))
        assertFailsWith<IllegalArgumentException> { downloadRangeHeader(-1, 1) }
        assertFailsWith<IllegalArgumentException> { downloadRangeHeader(2, 1) }
    }

    @Test
    fun `successful byte ranges continue without consuming retry attempts`() = runBlocking {
        val ranges = mutableListOf<LongRange>()
        val retries = mutableListOf<Int>()
        val sources = ArrayDeque(
            listOf(
                FakeDownloadSource(
                    statusCode = 206,
                    contentLength = 4,
                    contentRange = "bytes 0-3/8",
                    bytes = "abcd".encodeToByteArray(),
                ),
                FakeDownloadSource(
                    statusCode = 206,
                    contentLength = 4,
                    contentRange = "bytes 4-7/8",
                    bytes = "efgh".encodeToByteArray(),
                ),
            )
        )
        val output = Buffer()

        val completed = ResumableDownloadWriter(
            sourceFactory = DownloadSourceFactory { _, start, end ->
                ranges += start..end
                sources.removeFirst()
            },
            chunkSize = 4,
            retryDelay = {},
            onRetry = { attempt, _, _ -> retries += attempt },
        ).write(bookId, expectedSize = 8, output = output) { _, _ -> }

        assertEquals(8, completed)
        assertEquals(listOf(0L..3L, 4L..7L), ranges)
        assertEquals(emptyList(), retries)
        assertEquals("abcdefgh", output.readByteArray().decodeToString())
    }

    @Test
    fun `range end is bounded by total and avoids overflow`() {
        assertEquals(3, rangeEnd(offset = 0, total = 8, chunkSize = 4))
        assertEquals(7, rangeEnd(offset = 4, total = 8, chunkSize = 4))
        assertEquals(Long.MAX_VALUE, rangeEnd(Long.MAX_VALUE - 1, null, 4))
    }

    @Test
    fun `byte channel transfer preserves partial bytes before a stream failure`() = runBlocking {
        val channel = ByteChannel()
        val output = Buffer()
        val progress = mutableListOf<Long>()
        val failure = IllegalStateException("stream reset")
        val writer = launch {
            channel.writeFully("abcd".encodeToByteArray())
            channel.flush()
            yield()
            channel.cancel(failure)
        }

        assertFailsWith<ClosedByteChannelException> {
            transferByteChannelToSink(channel, output) { progress += it }
        }
        writer.join()

        assertEquals("abcd", output.readByteArray().decodeToString())
        assertEquals(listOf(4L), progress)
    }

    @Test
    fun `byte channel transfer writes a short successful response`() = runBlocking {
        val output = Buffer()

        val transferred = transferByteChannelToSink(
            channel = ByteReadChannel("done".encodeToByteArray()),
            output = output,
            onBytesWritten = {},
        )

        assertEquals(4L, transferred)
        assertEquals("done", output.readByteArray().decodeToString())
    }

    private class FakeDownloadSource(
        private val statusCode: Int,
        private val contentLength: Long? = null,
        private val contentRange: String? = null,
        private val bytes: ByteArray = byteArrayOf(),
        private val failure: Throwable? = null,
    ) : DownloadSource {
        override suspend fun transferTo(
            output: Sink,
            onResponse: suspend (DownloadResponseMetadata) -> Boolean,
            onBytesWritten: suspend (Long) -> Unit,
        ): Long {
            if (!onResponse(DownloadResponseMetadata(statusCode, contentLength, contentRange))) {
                return 0L
            }
            if (bytes.isNotEmpty()) {
                output.write(bytes)
                onBytesWritten(bytes.size.toLong())
            }
            failure?.let { throw it }
            return bytes.size.toLong()
        }
    }
}
