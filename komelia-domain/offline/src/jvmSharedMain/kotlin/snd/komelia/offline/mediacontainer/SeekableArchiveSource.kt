package snd.komelia.offline.mediacontainer

import java.io.Closeable
import java.io.File
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.READ
import java.security.MessageDigest

data class ArchiveSource(
    val identity: String,
    val displayName: String,
    val size: Long?,
    val modifiedMillis: Long?,
    val open: (checkCancelled: () -> Unit) -> SeekableArchiveInput,
    val verifyAccess: () -> Unit = {},
) {
    internal val identityHash get() = identity.archiveHash().take(24)
    internal fun cacheKey(session: String) = listOf(identity, size, modifiedMillis, if (modifiedMillis == null) session else "stable")
        .joinToString("\u0000").archiveHash().take(32)
}

class SeekableArchiveInput(channel: SeekableByteChannel, private val temporaryCopy: Closeable? = null) : Closeable {
    val channel: SeekableByteChannel = object : SeekableByteChannel by channel {
        override fun read(destination: ByteBuffer): Int = sourceOperation { channel.read(destination) }
        override fun position(): Long = sourceOperation { channel.position() }
        override fun position(newPosition: Long): SeekableByteChannel = apply { sourceOperation { channel.position(newPosition) } }
        override fun size(): Long = sourceOperation { channel.size() }
    }
    private var closed = false
    override fun close() = synchronized(this) {
        if (!closed) {
            closed = true
            try { channel.close() } finally { temporaryCopy?.close() }
        }
    }
}

fun fileArchiveSource(file: File): ArchiveSource = ArchiveSource(
    identity = file.canonicalPath,
    displayName = file.name,
    size = file.length().takeIf { file.exists() },
    modifiedMillis = file.lastModified().takeIf { it > 0 },
    open = { checkCancelled ->
        checkCancelled()
        SeekableArchiveInput(Files.newByteChannel(file.toPath(), READ))
    },
    verifyAccess = { Files.newByteChannel(file.toPath(), READ).use { } },
)

private inline fun <T> sourceOperation(block: () -> T): T = try { block() }
catch (error: java.io.IOException) { throw classifyArchiveError(error, sourceAccess = true) }

internal fun detectComicArchive(channel: SeekableByteChannel, checkCancelled: () -> Unit): ComicArchiveFormat {
    val position = channel.position()
    val header = ByteBuffer.allocate(8)
    try {
        channel.position(0)
        var emptyReads = 0
        while (header.hasRemaining()) {
            checkCancelled()
            val count = channel.read(header)
            if (count < 0) break
            if (count == 0 && ++emptyReads > 16) throw LocalArchiveAccessException(LocalArchiveFailure.SOURCE_IO)
        }
    } finally { channel.position(position) }
    val bytes = header.array().take(header.position()).map { it.toInt() and 255 }
    fun starts(vararg signature: Int) = bytes.take(signature.size) == signature.toList()
    return when {
        starts(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c) -> ComicArchiveFormat.SEVEN_ZIP
        starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x00) || starts(0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0x00) -> ComicArchiveFormat.RAR
        starts(0x50, 0x4b, 0x03, 0x04) || starts(0x50, 0x4b, 0x05, 0x06) || starts(0x50, 0x4b, 0x07, 0x08) -> ComicArchiveFormat.ZIP
        else -> throw LocalArchiveAccessException(LocalArchiveFailure.UNKNOWN_FORMAT)
    }
}

internal fun checkArchiveThread() {
    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Archive operation cancelled")
}

private fun String.archiveHash(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
