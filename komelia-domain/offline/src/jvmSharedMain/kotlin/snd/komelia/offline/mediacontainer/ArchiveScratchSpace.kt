package snd.komelia.offline.mediacontainer

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFileAttributeView

data class ArchiveCopyLimits(
    val perFileBytes: Long = 1L shl 30,
    val totalBytes: Long = 2L shl 30,
    val minimumFreeBytes: Long = 128L shl 20,
) {
    init { require(perFileBytes > 0 && totalBytes >= perFileBytes && minimumFreeBytes >= 0) }
}

/** One budget includes provider copies, in-progress conversions and committed 7z caches. */
class ArchiveScratchSpace(
    directory: File,
    val limits: ArchiveCopyLimits = ArchiveCopyLimits(),
    private val usableBytes: () -> Long = { directory.usableSpace },
) {
    val directory: File = directory.canonicalFile
    private data class Allocation(var reserved: Long, var active: Boolean = true)
    private val allocations = mutableMapOf<File, Allocation>()

    init {
        if (!this.directory.isDirectory && !this.directory.mkdirs()) throw IOException("Cannot create archive scratch directory")
        // Android's cache parent is private already; desktop's temporary-directory parent is not.
        Files.getFileAttributeView(this.directory.toPath(), PosixFileAttributeView::class.java)
            ?.setPermissions(PosixFilePermissions.fromString("rwx------"))
        this.directory.listFiles().orEmpty().filter { Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) }.forEach { file ->
            when {
                cacheName.matches(file.name) -> allocations[file] = Allocation(file.length(), false)
                orphanName.matches(file.name) -> if (!file.delete()) allocations[file] = Allocation(file.length(), false)
            }
        }
    }

    fun copy(
        expectedSize: Long?,
        checkCancelled: () -> Unit = ::checkArchiveThread,
        openInput: () -> InputStream,
    ): ScratchArchiveFile {
        checkCancelled()
        val lease = create(expectedSize?.coerceAtLeast(0) ?: 0, conversion = false)
        try {
            openInput().use { input ->
                lease.file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        checkCancelled()
                        copied += count
                        reserveForWrite(lease.file, copied)
                        output.write(buffer, 0, count)
                    }
                }
            }
            checkCancelled()
            synchronized(this) { allocations.getValue(lease.file).reserved = lease.file.length() }
            return lease
        } catch (e: Throwable) {
            lease.close()
            throw e
        }
    }

    @Synchronized
    fun create(expectedSize: Long = 0, conversion: Boolean = true): ScratchArchiveFile {
        // Only failed deletions are retried here; committed caches are evicted by their owner.
        allocations.filter { (file, allocation) -> !allocation.active && !cacheName.matches(file.name) }
            .keys.toList().forEach { if (!it.exists() || it.delete()) allocations.remove(it) }
        checkCapacity(expectedSize, expectedSize)
        val file = Files.createTempFile(directory.toPath(), if (conversion) "conversion-" else "archive-", if (conversion) ".part" else ".tmp").toFile()
        allocations[file] = Allocation(expectedSize)
        return ScratchArchiveFile(file, this)
    }

    /** Reserve before each write, including ZIP headers and central-directory writes. */
    fun outputChannel(file: File): SeekableByteChannel {
        val channel = Files.newByteChannel(file.toPath(), READ, WRITE)
        return object : SeekableByteChannel by channel {
            override fun write(src: ByteBuffer): Int {
                reserveForWrite(file, maxOf(channel.size(), Math.addExact(channel.position(), src.remaining().toLong())))
                return channel.write(src)
            }
        }
    }

    @Synchronized
    private fun reserveForWrite(file: File, newSize: Long) {
        val allocation = allocations.getValue(file)
        val growth = (newSize - allocation.reserved).coerceAtLeast(0)
        checkCapacity(newSize, growth)
        allocation.reserved += growth
    }

    private fun checkCapacity(fileSize: Long, additional: Long) {
        if (fileSize > limits.perFileBytes || allocations.values.sumOf { it.reserved } + additional > limits.totalBytes) {
            throw LocalArchiveAccessException(LocalArchiveFailure.COPY_LIMIT)
        }
        val unwritten = allocations.entries.sumOf { (file, allocation) -> (allocation.reserved - file.length()).coerceAtLeast(0) }
        if (usableBytes() - unwritten - additional < limits.minimumFreeBytes) {
            throw LocalArchiveAccessException(LocalArchiveFailure.LOW_SPACE)
        }
    }

    @Synchronized
    internal fun commit(file: File, name: String): File {
        require(cacheName.matches(name))
        val target = directory.resolve(name)
        // The service owns replacement and must retire an old entry before committing.
        check(!target.exists()) { "Archive cache already exists" }
        Files.move(file.toPath(), target.toPath(), ATOMIC_MOVE)
        allocations[target] = allocations.remove(file)!!.apply { reserved = target.length() }
        return target
    }

    @Synchronized
    fun existingCache(name: String): ScratchArchiveFile? {
        require(cacheName.matches(name))
        val file = directory.resolve(name)
        if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) return null
        allocations.getOrPut(file) { Allocation(file.length()) }.active = true
        file.setLastModified(System.currentTimeMillis())
        return ScratchArchiveFile(file, this)
    }

    @Synchronized
    fun cachedFiles(): List<File> = allocations.keys.filter { cacheName.matches(it.name) && it.isFile }

    @Synchronized
    internal fun release(file: File) {
        allocations[file]?.active = false
        if (!file.exists() || file.delete()) allocations.remove(file)
    }

    @Synchronized
    internal fun retain(file: File) { allocations[file]?.active = false }

    companion object {
        private val spaces = mutableMapOf<String, ArchiveScratchSpace>()
        internal val cacheName = Regex("seven-v1-[0-9a-f]{24}-[0-9a-f]{32}\\.zip")
        private val orphanName = Regex("(?:archive-.+\\.tmp|conversion-.+\\.part|seven-v[0-9]+-[0-9a-f-]+\\.zip)")

        fun forDirectory(directory: File): ArchiveScratchSpace = synchronized(spaces) {
            spaces.getOrPut(directory.canonicalPath) { ArchiveScratchSpace(directory) }
        }
    }
}

class ScratchArchiveFile internal constructor(file: File, private val space: ArchiveScratchSpace) : Closeable {
    var file: File = file
        private set
    private var closed = false

    internal fun commit(name: String) { file = space.commit(file, name) }

    internal fun retain() = synchronized(this) {
        if (!closed) { closed = true; space.retain(file) }
    }

    override fun close() = synchronized(this) {
        if (!closed) { closed = true; space.release(file) }
    }
}
