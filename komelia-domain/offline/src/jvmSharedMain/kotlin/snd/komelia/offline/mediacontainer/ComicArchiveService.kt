package snd.komelia.offline.mediacontainer

import com.github.junrar.Archive
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.MemoryLimitException
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.channels.Channels
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import snd.komelia.offline.local.isMultipartArchiveName

/** Shared by import and reading. Cache entries own channels/copies, never user source files. */
class ComicArchiveService(
    private val scratch: ArchiveScratchSpace,
    private val limits: SevenZipLimits = SevenZipLimits(),
    private val onConverted: () -> Unit = {},
) : Closeable {
    private data class Cached(
        val identity: String,
        val archive: OpenedComicArchive,
        val input: SeekableArchiveInput?,
        val converted: ScratchArchiveFile?,
    ) {
        fun close(deleteConverted: Boolean = true) {
            try { archive.close() } finally {
                try { input?.close() } finally { if (deleteConverted) converted?.close() else converted?.retain() }
            }
        }
    }

    private val lock = ReentrantLock()
    private val session = UUID.randomUUID().toString()
    private val archives = LinkedHashMap<String, Cached>(2, 0.75f, true)
    private val activeReaders = mutableMapOf<String, Int>()

    init { reclaimSlots(null, 0) }

    fun <T> withArchive(source: ArchiveSource, checkCancelled: () -> Unit = ::checkArchiveThread, block: (OpenedComicArchive) -> T): T {
        val key = source.cacheKey(session)
        try {
            return lock.withArchiveLock(checkCancelled) {
                try {
                    if (archives.containsKey(key)) {
                        try { source.verifyAccess() } catch (error: Throwable) { throw classifyArchiveError(error, sourceAccess = true) }
                    }
                    val archive = archives[key] ?: run {
                        archives.filterValues { it.identity == source.identity }.keys.toList().forEach(::retire)
                        val cacheName = "seven-v1-${source.identityHash}-$key.zip"
                        reclaimSlots(cacheName, 1)
                        val opened = openReclaimingIdleCaches(source, key, checkCancelled)
                        archives[key] = opened
                        opened
                    }
                    checkCancelled()
                    activeReaders[key] = activeReaders.getOrDefault(key, 0) + 1
                    try { block(archive.archive).also { checkCancelled() } }
                    finally {
                        val remaining = activeReaders.getValue(key) - 1
                        if (remaining == 0) activeReaders.remove(key) else activeReaders[key] = remaining
                    }
                } catch (error: Throwable) {
                    runCatching { retire(key) }.exceptionOrNull()?.let(error::addSuppressed)
                    throw error
                }
            }
        } catch (error: Throwable) {
            checkCancelled()
            throw classifyArchiveError(error)
        }
    }

    private fun open(source: ArchiveSource, key: String, checkCancelled: () -> Unit): Cached {
        if (isMultipartArchiveName(source.displayName)) {
            throw LocalArchiveAccessException(LocalArchiveFailure.MULTI_VOLUME)
        }
        val name = "seven-v1-${source.identityHash}-$key.zip"
        scratch.cachedFiles().filter { it.name.startsWith("seven-v1-${source.identityHash}-") && it.name != name }
            .forEach { scratch.release(it) }
        // Verify source access even on a disk-cache hit, including after permission revocation.
        val input = try { source.open(checkCancelled) } catch (error: Throwable) { throw classifyArchiveError(error, sourceAccess = true) }
        try {
            return when (val format = detectComicArchive(input.channel, checkCancelled)) {
                ComicArchiveFormat.ZIP -> Cached(source.identity, ZipComicArchive(openZip(input), format), input, null)
                ComicArchiveFormat.RAR -> {
                    val rar = Archive(Channels.newInputStream(input.channel))
                    try {
                        if (rar.isEncrypted || rar.isPasswordProtected) throw LocalArchiveAccessException(LocalArchiveFailure.ENCRYPTED)
                        if (rar.mainHeader?.isMultiVolume == true || rar.fileHeaders.any { it.isSplitBefore || it.isSplitAfter }) throw LocalArchiveAccessException(LocalArchiveFailure.MULTI_VOLUME)
                        Cached(source.identity, RarComicArchive(rar), input, null)
                    } catch (error: Throwable) { rar.close(); throw error }
                }
                ComicArchiveFormat.SEVEN_ZIP -> {
                    // A prior committed cache is reusable only after the actual type is checked.
                    var converted = scratch.existingCache(name)
                    if (converted != null) {
                        val cachedZip = runCatching { openZip(converted.file) }.getOrNull()
                        if (cachedZip != null) {
                            input.close()
                            return Cached(source.identity, ZipComicArchive(cachedZip, format), null, converted)
                        }
                        converted.close()
                        converted = null
                    }
                    try {
                        converted = SevenZipCacheConverter.convert(input.channel, scratch, name, limits, checkCancelled)
                        input.close()
                        checkCancelled()
                        onConverted()
                        val zip = openZip(converted.file)
                        Cached(source.identity, ZipComicArchive(zip, format), null, converted)
                    } catch (error: Throwable) { converted?.close(); throw error }
                }
            }
        } catch (error: Throwable) {
            runCatching { input.close() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun reclaimSlots(retainName: String?, requestedSlots: Int) {
        while (true) {
            val openedFiles = archives.values.mapNotNull { it.converted?.file }.toSet()
            val inactiveFiles = scratch.cachedFiles().filter { it !in openedFiles }.sortedBy { it.lastModified() }
            val extraSlots = if (inactiveFiles.any { it.name == retainName }) 0 else requestedSlots
            if (archives.size + inactiveFiles.size + extraSlots <= MAX_ARCHIVES) return
            val victim = inactiveFiles.firstOrNull { it.name != retainName }
            if (victim != null) {
                scratch.release(victim)
                if (victim.exists()) throw LocalArchiveAccessException(LocalArchiveFailure.LOW_SPACE, IOException("Cannot retire archive cache"))
            }
            else {
                val key = archives.keys.firstOrNull { it !in activeReaders } ?: throw LocalArchiveAccessException(LocalArchiveFailure.COPY_LIMIT)
                retire(key)
            }
        }
    }

    private fun openReclaimingIdleCaches(source: ArchiveSource, key: String, checkCancelled: () -> Unit): Cached = try {
        open(source, key, checkCancelled)
    } catch (error: LocalArchiveAccessException) {
        val reclaimable = archives.keys.filter { it !in activeReaders }
        val activeFiles = archives.filterKeys { it in activeReaders }.values.mapNotNull { it.converted?.file }.toSet()
        val inactiveFiles = scratch.cachedFiles().filter { it !in activeFiles }
        if (error.reason !in listOf(LocalArchiveFailure.COPY_LIMIT, LocalArchiveFailure.LOW_SPACE) ||
            (reclaimable.isEmpty() && inactiveFiles.isEmpty())) throw error
        reclaimable.forEach(::retire)
        inactiveFiles.forEach { scratch.release(it) }
        // Retry the same format once, only after reclaiming cache space. No parser fallback.
        open(source, key, checkCancelled)
    }

    private fun retire(key: String) { archives.remove(key)?.close() }

    /** Completed disk caches survive process recreation; partial/provider copies never do. */
    override fun close() = lock.withArchiveLock({}) {
        val previous = archives.values.toList()
        archives.clear()
        previous.forEach { it.close(deleteConverted = false) }
    }

    companion object {
        private const val MAX_ARCHIVES = 2
        private val services = mutableMapOf<String, ComicArchiveService>()
        fun forDirectory(directory: File): ComicArchiveService = synchronized(services) {
            services.getOrPut(directory.canonicalPath) { ComicArchiveService(ArchiveScratchSpace.forDirectory(directory)) }
        }
    }
}

private fun openZip(input: SeekableArchiveInput): ZipFile = ZipFile.builder().setSeekableByteChannel(input.channel)
    .setUseUnicodeExtraFields(true).setIgnoreLocalFileHeader(true).get()

private fun openZip(file: File): ZipFile = ZipFile.builder().setFile(file)
    .setUseUnicodeExtraFields(true).setIgnoreLocalFileHeader(true).get()

private class ZipComicArchive(private val zip: ZipFile, override val format: ComicArchiveFormat) : OpenedComicArchive {
    init {
        if (zip.entries.asSequence().any { it.generalPurposeBit.usesEncryption() }) {
            zip.close()
            throw LocalArchiveAccessException(LocalArchiveFailure.ENCRYPTED)
        }
    }
    override val entries = zip.entries.asSequence().filterNot { it.isDirectory }.map { ComicArchiveEntry(it.name, it.size.takeIf { n -> n >= 0 }) }.toList()
    override fun readEntryBytes(name: String, checkCancelled: () -> Unit): ByteArray {
        val match = resolveArchiveEntry(entries, name)
        val entry = zip.getEntry(match.name) ?: throw LocalArchiveAccessException(LocalArchiveFailure.CORRUPT)
        if (!zip.canReadEntryData(entry)) throw LocalArchiveAccessException(LocalArchiveFailure.UNSUPPORTED_CODEC)
        return zip.getInputStream(entry).use { it.readArchiveBytes(checkCancelled, if (format == ComicArchiveFormat.SEVEN_ZIP) 64L shl 20 else Long.MAX_VALUE) }
    }
    override fun close() = zip.close()
}

private class RarComicArchive(private val archive: Archive) : OpenedComicArchive {
    override val format = ComicArchiveFormat.RAR
    override val entries = archive.fileHeaders.filterNot { it.isDirectory }.map { ComicArchiveEntry(it.fileName, it.fullUnpackSize) }
    override fun readEntryBytes(name: String, checkCancelled: () -> Unit): ByteArray {
        val match = resolveArchiveEntry(entries, name)
        val header = archive.fileHeaders.first { it.fileName == match.name }
        return archive.getInputStream(header).use { it.readArchiveBytes(checkCancelled) }
    }
    override fun close() = archive.close()
}

internal fun resolveArchiveEntry(entries: List<ComicArchiveEntry>, name: String): ComicArchiveEntry {
    entries.firstOrNull { it.name == name }?.let { return it }
    fun String.normalized() = runCatching { substringBefore('#').substringBefore('?').decodeURLPart() }.getOrDefault(this)
        .replace('\\', '/').trimStart('/')
    val requested = name.normalized()
    val normalized = entries.map { it to it.name.normalized() }
    return normalized.firstOrNull { it.second == requested }?.first
        ?: normalized.firstOrNull { it.second.endsWith("/$requested") }?.first
        ?: normalized.filter { it.second.substringAfterLast('/') == requested.substringAfterLast('/') }.singleOrNull()?.first
        ?: throw IOException("Archive entry does not exist")
}

private fun InputStream.readArchiveBytes(checkCancelled: () -> Unit, limit: Long = Long.MAX_VALUE): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        checkCancelled()
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        if (total > limit) throw LocalArchiveAccessException(LocalArchiveFailure.DECODE_LIMIT)
        output.write(buffer, 0, count)
    }
    checkCancelled()
    return output.toByteArray()
}

internal fun classifyArchiveError(error: Throwable, sourceAccess: Boolean = false): Throwable {
    val chain = generateSequence(error) { it.cause }.take(20).toList()
    chain.filterIsInstance<LocalArchiveAccessException>().firstOrNull()?.let { return it }
    chain.filterIsInstance<CancellationException>().firstOrNull()?.let { return it }
    if (chain.any { it is InterruptedException } || (Thread.currentThread().isInterrupted && chain.any { it is InterruptedIOException })) {
        return CancellationException("Archive operation cancelled", error)
    }
    val reason = when {
        chain.any { it is SecurityException || it is AccessDeniedException } -> LocalArchiveFailure.PERMISSION
        chain.any { it is MemoryLimitException } -> LocalArchiveFailure.MEMORY_LIMIT
        chain.any { it is PasswordRequiredException || it.javaClass.simpleName.contains("Encrypted") } -> LocalArchiveFailure.ENCRYPTED
        chain.any { it is FileSystemException && it.reason?.contains("space", true) == true } -> LocalArchiveFailure.LOW_SPACE
        sourceAccess -> LocalArchiveFailure.SOURCE_IO
        chain.any { it.javaClass.simpleName.startsWith("Unsupported") || it.message?.startsWith("Unsupported", true) == true } -> LocalArchiveFailure.UNSUPPORTED_CODEC
        error is IOException || error is com.github.junrar.exception.RarException || error is ArithmeticException || error is IllegalArgumentException -> LocalArchiveFailure.CORRUPT
        else -> return error
    }
    return LocalArchiveAccessException(reason, error)
}
