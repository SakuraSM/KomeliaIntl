package snd.komelia.offline.mediacontainer.divina

import io.github.vinceglb.filekit.PlatformFile
import io.ktor.http.decodeURLPart
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import kotlin.sequences.asSequence

class ZipExtractor {
    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        val zipFile = ZipFile
            .builder()
            .setFile(file.file)
            .setUseUnicodeExtraFields(true)
            .setIgnoreLocalFileHeader(true)
            .get()

        val bytes = zipFile.use { zip ->
            val entry = zip.getEntry(entryName)
                ?: findBestMatch(zip.entries.asSequence().filterNot { it.isDirectory }.toList(), entryName)

            entry
                ?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
        }

        if (bytes == null) throw IllegalStateException("zip entry does not exist: $entryName")
        return bytes
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
}
