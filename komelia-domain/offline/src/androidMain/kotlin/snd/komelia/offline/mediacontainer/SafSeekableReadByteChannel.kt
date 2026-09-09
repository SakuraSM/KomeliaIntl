package snd.komelia.offline.mediacontainer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.ErrnoException
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

class SafSeekableReadByteChannel(
    treeUri: Uri,
    context: Context,
) : SeekableByteChannel {
    private val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(treeUri, "r")
        ?: error("Failed to open file descriptor $treeUri")
    private val fileStream: FileInputStream = FileInputStream(pfd.fileDescriptor)
    private val fileChannel = fileStream.channel
    val descriptorSize: Long get() = pfd.statSize

    internal fun verifyRandomAccess() {
        checkOpen()
        try {
            Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_CUR)
        } catch (e: ErrnoException) {
            // Keep errno so the opener can distinguish ESPIPE from a real I/O failure.
            throw IOException("Cannot seek archive provider", e)
        }
    }

    override fun position(): Long {
        checkOpen()
        return fileChannel.position()
    }

    override fun position(newPosition: Long): SeekableByteChannel {
        require(newPosition >= 0) { "Negative channel position" }
        checkOpen()
        fileChannel.position(newPosition)
        return this
    }

    override fun read(dst: ByteBuffer): Int {
        checkOpen()
        if (!dst.hasRemaining()) return 0
        // Validate seekability instead of silently accepting a pipe as a random-access archive.
        fileChannel.position(fileChannel.position())
        return fileChannel.read(dst)
    }

    override fun size(): Long {
        checkOpen()
        return fileChannel.size()
    }

    override fun truncate(size: Long): SeekableByteChannel {
        checkOpen()
        throw NonWritableChannelException()
    }

    override fun write(src: ByteBuffer): Int {
        checkOpen()
        throw NonWritableChannelException()
    }

    override fun close() {
        try {
            fileStream.close()
        } finally {
            pfd.close()
        }
    }

    override fun isOpen(): Boolean {
        return fileChannel.isOpen
    }

    private fun checkOpen() {
        if (!isOpen) throw ClosedChannelException()
    }
}
