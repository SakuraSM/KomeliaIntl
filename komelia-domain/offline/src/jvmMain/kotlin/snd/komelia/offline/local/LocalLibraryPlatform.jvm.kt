package snd.komelia.offline.local

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import snd.komelia.offline.mediacontainer.desktopComicArchives
import snd.komelia.offline.mediacontainer.desktopComicArchiveCacheDirectory
import snd.komelia.offline.mediacontainer.fileArchiveSource
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
        val rootPath = File(root).canonicalFile.toPath()
        val cachePath = desktopComicArchiveCacheDirectory().toPath()
        val caller = currentCoroutineContext()
        rootPath.walk()
            .filter {
                caller.ensureActive()
                !it.startsWith(cachePath) && it.isRegularFile() && !it.toRealPath().startsWith(cachePath) &&
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
        if (isMultipartArchiveName(file.displayName)) throw snd.komelia.offline.mediacontainer.LocalArchiveAccessException(snd.komelia.offline.mediacontainer.LocalArchiveFailure.MULTI_VOLUME)
        when (file.displayName.substringAfterLast('.', "").lowercase()) {
            "cbz", "zip", "cbr", "rar", "7z", "cb7" -> {
                val caller = currentCoroutineContext()
                runInterruptible(Dispatchers.IO) {
                    desktopComicArchives().withArchive(fileArchiveSource(file.file.file), caller::ensureActive) { archive ->
                        inspectComicArchive(
                            entries = archive.entries.map { it.name to it.size },
                            readEntry = { archive.readEntryBytes(it, caller::ensureActive) },
                            mediaType = archive.format.mediaType,
                        )
                    }
                }
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
