package snd.komelia.offline.mediacontainer.divina

import io.github.vinceglb.filekit.PlatformFile
import io.ktor.http.decodeURLPart
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.util.LinkedHashMap
import kotlin.sequences.asSequence

class ZipExtractor {
    private val archiveLock = Any()
    private val openArchives = LinkedHashMap<String, ZipFile>(MAX_OPEN_ARCHIVES, 0.75f, true)

    fun prepare(file: PlatformFile) = synchronized(archiveLock) {
        val key = file.toString()
        openArchives.remove(key)?.close()
        openArchives[key] = openArchive(file)
        trimOpenArchives()
    }

    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        val bytes = synchronized(archiveLock) {
            val key = file.toString()
            val zip = openArchives[key] ?: openArchive(file).also {
                openArchives[key] = it
                trimOpenArchives()
            }
            val entry = zip.getEntry(entryName)
                ?: findBestMatch(zip.entries.asSequence().filterNot { it.isDirectory }.toList(), entryName)

            entry
                ?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
        }

        if (bytes == null) throw IllegalStateException("zip entry does not exist: $entryName")
        return bytes
    }

    private fun openArchive(file: PlatformFile): ZipFile = ZipFile
        .builder()
        .setFile(file.file)
        .setUseUnicodeExtraFields(true)
        .setIgnoreLocalFileHeader(true)
        .get()

    private fun trimOpenArchives() {
        while (openArchives.size > MAX_OPEN_ARCHIVES) {
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
