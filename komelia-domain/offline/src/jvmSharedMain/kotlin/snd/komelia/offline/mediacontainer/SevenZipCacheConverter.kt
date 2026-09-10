package snd.komelia.offline.mediacontainer

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.CRC32

data class SevenZipLimits(
    val entryBytes: Long = 64L shl 20,
    val totalBytes: Long = 1L shl 30,
    val entryCount: Int = 10_000,
    val memoryKiB: Int = 128 * 1024,
) {
    init { require(entryBytes > 0 && totalBytes >= entryBytes && entryCount > 0 && memoryKiB > 0) }
}

/** One sequential pass also validates entries that are not image pages. Never extracts paths. */
internal object SevenZipCacheConverter {
    private val conversionLock = ReentrantLock()

    fun convert(
        input: SeekableByteChannel,
        space: ArchiveScratchSpace,
        cacheName: String,
        limits: SevenZipLimits,
        checkCancelled: () -> Unit,
    ): ScratchArchiveFile = conversionLock.withArchiveLock(checkCancelled) {
        ArchivePreparation.started()
        try {
            val output = space.create()
            try {
                SevenZFile.builder().setSeekableByteChannel(input).setMaxMemoryLimitKiB(limits.memoryKiB)
                    .setTryToRecoverBrokenArchives(false).get().use { archive ->
                        val entries = archive.entries.toList()
                        validateEntries(entries, limits, checkCancelled)
                        space.outputChannel(output.file).use { channel ->
                            ZipArchiveOutputStream(channel).use { zip ->
                                val buffer = ByteArray(64 * 1024)
                                var total = 0L
                                while (true) {
                                    checkCancelled()
                                    val entry = archive.nextEntry ?: break
                                    if (entry.isDirectory) continue
                                    if (entry.contentMethods?.any { it.method == SevenZMethod.AES256SHA256 } == true) {
                                        throw LocalArchiveAccessException(LocalArchiveFailure.ENCRYPTED)
                                    }
                                    zip.putArchiveEntry(ZipArchiveEntry(entry.name).apply { method = ZipArchiveOutputStream.STORED })
                                    var read = 0L
                                    var emptyReads = 0
                                    val crc = CRC32()
                                    while (true) {
                                        checkCancelled()
                                        val count = archive.read(buffer)
                                        if (count < 0) break
                                        if (count == 0) {
                                            if (++emptyReads > 16) throw LocalArchiveAccessException(LocalArchiveFailure.CORRUPT)
                                            continue
                                        }
                                        emptyReads = 0
                                        checkCancelled()
                                        read = Math.addExact(read, count.toLong())
                                        total = Math.addExact(total, count.toLong())
                                        if (read > limits.entryBytes || total > limits.totalBytes) throw LocalArchiveAccessException(LocalArchiveFailure.DECODE_LIMIT)
                                        if (read > entry.size) throw LocalArchiveAccessException(LocalArchiveFailure.CORRUPT)
                                        crc.update(buffer, 0, count)
                                        zip.write(buffer, 0, count)
                                    }
                                    if (read != entry.size || (entry.hasCrc && crc.value != entry.crcValue)) {
                                        throw LocalArchiveAccessException(LocalArchiveFailure.CORRUPT)
                                    }
                                    zip.closeArchiveEntry()
                                }
                                checkCancelled()
                                zip.finish()
                            }
                        }
                    }
                checkCancelled()
                output.commit(cacheName)
                output
            } catch (error: Throwable) {
                output.close()
                throw error
            }
        } finally { ArchivePreparation.finished() }
    }

    private fun validateEntries(entries: List<SevenZArchiveEntry>, limits: SevenZipLimits, checkCancelled: () -> Unit) {
        if (entries.size > limits.entryCount) throw LocalArchiveAccessException(LocalArchiveFailure.DECODE_LIMIT)
        val names = HashSet<String>()
        var total = 0L
        for (entry in entries) {
            checkCancelled()
            val name = entry.name?.replace('\\', '/')?.removeSuffix("/")
            val unixType = (entry.windowsAttributes ushr 16) and 0xf000
            if (name.isNullOrEmpty() || name.startsWith('/') || Regex("^[A-Za-z]:").containsMatchIn(name) ||
                name.any { it.code < 32 } || name.split('/').any { it == ".." || it == "." || it.isEmpty() } ||
                !names.add(name) || entry.isAntiItem || (entry.hasWindowsAttributes && (unixType == 0xa000 || entry.windowsAttributes and 0x400 != 0))
            ) throw LocalArchiveAccessException(LocalArchiveFailure.UNSAFE_ENTRY)
            if (entry.size < 0) throw LocalArchiveAccessException(LocalArchiveFailure.CORRUPT)
            if (entry.size > limits.entryBytes || entry.size > limits.totalBytes - total) throw LocalArchiveAccessException(LocalArchiveFailure.DECODE_LIMIT)
            total += entry.size
        }
    }
}

internal fun <T> ReentrantLock.withArchiveLock(checkCancelled: () -> Unit, block: () -> T): T {
    while (true) {
        checkCancelled()
        if (tryLock(50, TimeUnit.MILLISECONDS)) break
    }
    try { checkCancelled(); return block() } finally { unlock() }
}
