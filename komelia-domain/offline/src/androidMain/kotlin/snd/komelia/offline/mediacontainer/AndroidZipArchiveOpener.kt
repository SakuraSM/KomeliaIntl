package snd.komelia.offline.mediacontainer

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/** Shared by import inspection and page/resource extraction. Owns every opened channel/copy. */
class AndroidZipArchiveOpener(
    private val context: Context,
    private val scratch: ArchiveScratchSpace = ArchiveScratchSpace.forContext(context),
) {
    fun open(
        file: PlatformFile,
        expectedSize: Long? = null,
        checkCancelled: () -> Unit = ::checkArchiveThread,
    ): OpenedZipArchive {
        checkCancelled()
        return when (val source = file.androidFile) {
            is AndroidFile.FileWrapper -> OpenedZipArchive(builder().setFile(source.file).get())
            is AndroidFile.UriWrapper -> {
                val channel = SafSeekableReadByteChannel(source.uri, context)
                var declaredSize: Long? = expectedSize?.takeIf { it >= 0 }
                val needsCopy = try {
                    val descriptorSize = channel.descriptorSize
                    declaredSize = declaredSize ?: descriptorSize.takeIf { it >= 0 }
                    channel.verifyRandomAccess()
                    channel.position(channel.position())
                    val size = channel.size()
                    size < 0 || (size == 0L && descriptorSize < 0)
                } catch (e: IOException) {
                    if (e.isNonSeekable()) true else {
                        runCatching { channel.close() }.exceptionOrNull()?.let(e::addSuppressed)
                        throw e
                    }
                } catch (e: Exception) {
                    runCatching { channel.close() }.exceptionOrNull()?.let(e::addSuppressed)
                    throw e
                }
                if (!needsCopy) {
                    try {
                        // ZIP parsing errors must not trigger a second download/copy.
                        OpenedZipArchive(builder().setSeekableByteChannel(channel).get())
                    } catch (e: Throwable) {
                        runCatching { channel.close() }.exceptionOrNull()?.let(e::addSuppressed)
                        throw e
                    }
                } else {
                    channel.close()
                    checkCancelled()
                    val copy = scratch.copy(declaredSize, checkCancelled) {
                        context.contentResolver.openInputStream(source.uri)
                            ?: throw IOException("File provider did not return an archive stream")
                    }
                    try {
                        checkCancelled()
                        OpenedZipArchive(builder().setFile(copy.file).get(), copy)
                    } catch (e: Throwable) {
                        copy.close()
                        throw e
                    }
                }
            }
        }
    }

    private fun builder() = ZipFile.builder().setUseUnicodeExtraFields(true).setIgnoreLocalFileHeader(true)

    private fun Throwable.isNonSeekable(): Boolean = generateSequence(this) { it.cause }
        .any { it is ErrnoException && it.errno == OsConstants.ESPIPE }
}

class OpenedZipArchive(val zip: ZipFile, private val scratchFile: Closeable? = null) : Closeable {
    override fun close() {
        try { zip.close() } finally { scratchFile?.close() }
    }
}

data class ArchiveCopyLimits(
    val perFileBytes: Long = 1L shl 30,
    val totalBytes: Long = 2L shl 30,
    val minimumFreeBytes: Long = 128L shl 20,
) {
    init {
        require(perFileBytes > 0 && totalBytes >= perFileBytes && minimumFreeBytes >= 0)
    }
}

/** Process-wide reservations account for concurrent scans, copies and the two reader archives. */
class ArchiveScratchSpace(
    private val directory: File,
    private val limits: ArchiveCopyLimits = ArchiveCopyLimits(),
    private val usableBytes: () -> Long = { directory.usableSpace },
) {
    private data class Allocation(var reserved: Long, var active: Boolean = true)
    private val allocations = mutableMapOf<File, Allocation>()

    init {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create archive scratch directory")
        // This directory belongs only to this feature; never traverse or delete source files.
        directory.listFiles()?.filter {
            it.name.startsWith("archive-") && it.name.endsWith(".tmp") &&
                it.isFile && it.canonicalFile.parentFile == directory.canonicalFile
        }?.forEach {
            if (!it.delete()) allocations[it] = Allocation(it.length(), active = false)
        }
    }

    fun copy(
        expectedSize: Long?,
        checkCancelled: () -> Unit = ::checkArchiveThread,
        openInput: () -> InputStream,
    ): ScratchArchiveFile {
        checkCancelled()
        val file = allocate(expectedSize?.coerceAtLeast(0) ?: 0)
        try {
            openInput().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        checkCancelled()
                        copied += count
                        reserveForWrite(file, copied)
                        output.write(buffer, 0, count)
                    }
                }
            }
            checkCancelled()
            synchronized(this) { allocations.getValue(file).reserved = file.length() }
            return ScratchArchiveFile(file) { release(file) }
        } catch (e: Throwable) {
            release(file)
            throw e
        }
    }

    @Synchronized
    private fun allocate(expectedSize: Long): File {
        allocations.filterValues { !it.active }.keys.toList().forEach {
            if (!it.exists() || it.delete()) allocations.remove(it)
        }
        checkCapacity(expectedSize, expectedSize)
        val file = File.createTempFile("archive-", ".tmp", directory)
        allocations[file] = Allocation(expectedSize)
        return file
    }

    @Synchronized
    private fun reserveForWrite(file: File, newSize: Long) {
        val allocation = allocations.getValue(file)
        val growth = (newSize - allocation.reserved).coerceAtLeast(0)
        checkCapacity(newSize, growth)
        allocation.reserved += growth
    }

    private fun checkCapacity(fileSize: Long, additionalReservation: Long) {
        if (fileSize > limits.perFileBytes || allocations.values.sumOf { it.reserved } + additionalReservation > limits.totalBytes) {
            throw LocalArchiveAccessException(LocalArchiveFailure.COPY_LIMIT)
        }
        val unwrittenReservations = allocations.entries.sumOf { (file, allocation) ->
            (allocation.reserved - file.length()).coerceAtLeast(0)
        }
        if (usableBytes() - unwrittenReservations - additionalReservation < limits.minimumFreeBytes) {
            throw LocalArchiveAccessException(LocalArchiveFailure.LOW_SPACE)
        }
    }

    @Synchronized
    private fun release(file: File) {
        allocations[file]?.active = false
        if (!file.exists() || file.delete()) allocations.remove(file)
    }

    companion object {
        private val spaces = mutableMapOf<String, ArchiveScratchSpace>()

        fun forContext(context: Context): ArchiveScratchSpace = synchronized(spaces) {
            val root = context.cacheDir.resolve("komelia-saf-archives")
            spaces.getOrPut(root.canonicalPath) { ArchiveScratchSpace(root) }
        }
    }
}

class ScratchArchiveFile(val file: File, private val release: () -> Unit) : Closeable {
    override fun close() = release()
}

internal fun checkArchiveThread() {
    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Archive copy cancelled")
}
