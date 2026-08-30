package snd.komelia.offline.local

import com.github.junrar.Archive
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import kotlin.io.path.fileSize
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.sequences.asSequence

actual fun createLocalLibraryPlatform(): LocalLibraryPlatform? = DesktopLocalLibraryPlatform()

private class DesktopLocalLibraryPlatform : LocalLibraryPlatform {
    override suspend fun listSupportedFiles(root: String): List<LocalLibraryFile> = withContext(Dispatchers.IO) {
        val rootPath = File(root).toPath()
        rootPath.walk()
            .filter {
                it.isRegularFile() &&
                    isSupportedLocalBook(it.name) &&
                    !it.name.endsWith(".pdf", ignoreCase = true)
            }
            .map { path ->
                LocalLibraryFile(
                    file = PlatformFile(path.toFile()),
                    relativePath = path.relativeTo(rootPath).toString().replace('\\', '/'),
                    displayName = path.name,
                    sizeBytes = path.fileSize(),
                    lastModifiedEpochMillis = path.getLastModifiedTime().toMillis(),
                )
            }
            .sortedBy { it.relativePath.lowercase() }
            .toList()
    }

    override suspend fun inspect(file: LocalLibraryFile): LocalBookInspection = withContext(Dispatchers.IO) {
        when (file.displayName.substringAfterLast('.', "").lowercase()) {
            "cbz", "zip" -> withZip(file.file) { zip ->
                val entries = zip.entries.asSequence().filterNot { it.isDirectory }.toList()
                inspectComicArchive(
                    entries = entries.map { it.name to it.size.takeIf { size -> size >= 0 } },
                    readEntry = { name -> zip.getInputStream(zip.getEntry(name)).use { it.readBytes() } },
                    mediaType = "application/zip",
                )
            }
            "epub" -> withZip(file.file) { zip ->
                val entries = zip.entries.asSequence().filterNot { it.isDirectory }.toList()
                inspectEpubArchive(
                    entries = entries.map { it.name },
                    readEntry = { name ->
                        val entry = zip.getEntry(name) ?: error("EPUB entry does not exist: $name")
                        zip.getInputStream(entry).use { it.readBytes() }
                    },
                )
            }
            "cbr", "rar" -> Archive(file.file.file).use { archive ->
                val headers = archive.fileHeaders.filterNot { it.isDirectory }
                inspectComicArchive(
                    entries = headers.map { it.fileName to it.fullUnpackSize },
                    readEntry = { name ->
                        val header = headers.firstOrNull { it.fileName == name } ?: error("RAR entry does not exist: $name")
                        archive.getInputStream(header).use { it.readBytes() }
                    },
                    mediaType = "application/x-rar-compressed",
                )
            }
            "pdf" -> error("Local PDF import is not available on desktop yet")
            else -> error("Unsupported local book: ${file.displayName}")
        }
    }

    private fun <T> withZip(file: PlatformFile, block: (ZipFile) -> T): T =
        ZipFile.builder()
            .setFile(file.file)
            .setUseUnicodeExtraFields(true)
            .setIgnoreLocalFileHeader(true)
            .get()
            .use(block)
}
