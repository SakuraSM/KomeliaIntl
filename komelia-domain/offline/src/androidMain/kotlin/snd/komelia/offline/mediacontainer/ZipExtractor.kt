package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import io.ktor.http.decodeURLPart
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import java.util.LinkedHashMap
import kotlin.sequences.asSequence

class ZipExtractor(
    private val opener: AndroidZipArchiveOpener = AndroidZipArchiveOpener(FileKit.context),
) : java.io.Closeable {
    private val archiveLock = Any()
    private val openArchives = LinkedHashMap<String, OpenedZipArchive>(MAX_OPEN_ARCHIVES, 0.75f, true)

    fun prepare(file: PlatformFile, checkCancelled: () -> Unit = ::checkArchiveThread) = synchronized(archiveLock) {
        checkCancelled()
        val key = file.toString()
        openArchives.remove(key)?.close()
        trimOpenArchives(MAX_OPEN_ARCHIVES - 1)
        openArchives[key] = openArchive(file, checkCancelled)
        trimOpenArchives()
    }

    fun getEntryBytes(file: PlatformFile, entryName: String, checkCancelled: () -> Unit = ::checkArchiveThread): ByteArray {
        val bytes = synchronized(archiveLock) {
            val key = file.toString()
            try {
                checkCancelled()
                val zip = (openArchives[key] ?: run {
                    // Release the eldest copy before reserving space for the next one.
                    trimOpenArchives(MAX_OPEN_ARCHIVES - 1)
                    openArchive(file, checkCancelled).also { openArchives[key] = it }
                }).zip
                val entry = zip.getEntry(entryName)
                    ?: findBestMatch(zip.entries.asSequence().filterNot { it.isDirectory }.toList(), entryName)
                entry?.let { zip.getInputStream(it).use { stream -> stream.readBytes() } }.also { checkCancelled() }
            } catch (error: Throwable) {
                runCatching { openArchives.remove(key)?.close() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }

        if (bytes == null) error("zip entry does not exist: $entryName")
        return bytes
    }

    private fun openArchive(file: PlatformFile, checkCancelled: () -> Unit): OpenedZipArchive =
        opener.open(file, checkCancelled = checkCancelled)

    override fun close() = synchronized(archiveLock) {
        val archives = openArchives.values.toList()
        openArchives.clear()
        archives.forEach { it.close() }
    }

    private fun trimOpenArchives(maximumSize: Int = MAX_OPEN_ARCHIVES) {
        while (openArchives.size > maximumSize) {
            val eldest = openArchives.entries.iterator().next()
            val archive = eldest.value
            openArchives.remove(eldest.key)
            archive.close()
        }
    }

    private fun findBestMatch(entries: List<ZipArchiveEntry>, entryName: String): ZipArchiveEntry? {
        val requested = entryName.normalizeEntryName()
        if (requested.isBlank()) return null

        val normalizedEntries = entries.map { entry -> entry to entry.name.normalizeEntryName() }
        return normalizedEntries.firstOrNull { (_, name) -> name == requested }?.first
            ?: normalizedEntries.firstOrNull { (_, name) -> name.endsWith("/$requested") }?.first
            ?: normalizedEntries
                .filter { (_, name) -> name.substringAfterLast('/') == requested.substringAfterLast('/') }
                .singleOrNull()
                ?.first
    }

    private fun String.normalizeEntryName(): String {
        return substringBefore('#')
            .substringBefore('?')
            .safeDecodeURLPart()
            .replace('\\', '/')
            .trimStart('/')
    }

    private fun String.safeDecodeURLPart(): String = runCatching { decodeURLPart() }.getOrDefault(this)

    private companion object {
        const val MAX_OPEN_ARCHIVES = 2
    }
}
