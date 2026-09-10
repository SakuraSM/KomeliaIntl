package snd.komelia.offline.local

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.apache.commons.compress.archivers.zip.ZipFile
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komelia.offline.mediacontainer.AndroidPdfExtractor
import snd.komelia.offline.mediacontainer.AndroidZipArchiveOpener
import snd.komelia.offline.mediacontainer.AndroidArchiveSourceAccess
import snd.komelia.offline.mediacontainer.ComicArchiveService
import java.io.File
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.MediaProfile
import kotlin.sequences.asSequence

actual fun createLocalLibraryPlatform(): LocalLibraryPlatform? = AndroidLocalLibraryPlatform()

private class AndroidLocalLibraryPlatform : LocalLibraryPlatform {
    private val zipOpener by lazy { AndroidZipArchiveOpener(FileKit.context) }
    private val archiveAccess by lazy { AndroidArchiveSourceAccess(FileKit.context) }
    private val archives by lazy { ComicArchiveService.forDirectory(File(FileKit.context.cacheDir, "komelia-saf-archives")) }
    override val scheduledScanningIsManagedByPlatform: Boolean = true

    override suspend fun listSupportedFiles(root: String): List<LocalLibraryFile> = withContext(Dispatchers.IO) {
        val rootFile = PlatformFile(root)
        val rootDocument = when (val androidFile = rootFile.androidFile) {
            is AndroidFile.UriWrapper -> DocumentFile.fromTreeUri(FileKit.context, androidFile.uri)
            is AndroidFile.FileWrapper -> DocumentFile.fromFile(androidFile.file)
        } ?: error("Selected directory is unavailable")

        buildList { collectFiles(rootDocument, "", this) }
    }

    override suspend fun inspect(file: LocalLibraryFile): LocalBookInspection = withContext(Dispatchers.IO) {
        if (isMultipartArchiveName(file.displayName)) throw snd.komelia.offline.mediacontainer.LocalArchiveAccessException(snd.komelia.offline.mediacontainer.LocalArchiveFailure.MULTI_VOLUME)
        when (file.displayName.substringAfterLast('.', "").lowercase()) {
            "cbz", "zip", "cbr", "rar", "7z", "cb7" -> {
                val caller = currentCoroutineContext()
                runInterruptible(Dispatchers.IO) {
                    archives.withArchive(archiveAccess.source(file.file, file.sizeBytes, file.lastModifiedEpochMillis), caller::ensureActive) { archive ->
                        inspectComicArchive(
                            entries = archive.entries.map { it.name to it.size },
                            readEntry = { archive.readEntryBytes(it, caller::ensureActive) },
                            mediaType = archive.format.mediaType,
                        )
                    }
                }
            }
            "epub" -> withZip(file) { zip ->
                val entries = zip.entries.asSequence().filterNot { it.isDirectory }.toList()
                inspectEpubArchive(
                    entries = entries.map { it.name },
                    readEntry = { name ->
                        val entry = zip.getEntry(name) ?: error("EPUB entry does not exist: $name")
                        zip.getInputStream(entry).use { it.readBytes() }
                    },
                )
            }
            "pdf" -> inspectPdf(file.file)
            else -> error("Unsupported local book: ${file.displayName}")
        }
    }

    private suspend fun collectFiles(document: DocumentFile, prefix: String, output: MutableList<LocalLibraryFile>) {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val children = document.listFiles().mapNotNull { child ->
            caller.ensureActive()
            child.name?.let { child to it }
        }.sortedBy { it.second.lowercase() }
        children.forEach { (child, name) ->
            caller.ensureActive()
            val relativePath = if (prefix.isBlank()) name else "$prefix/$name"
            when {
                child.isDirectory -> collectFiles(child, relativePath, output)
                child.isFile && isSupportedLocalBook(name) -> output += LocalLibraryFile(
                    file = PlatformFile(child.uri),
                    relativePath = relativePath,
                    displayName = name,
                    sizeBytes = child.length(),
                    lastModifiedEpochMillis = child.lastModified(),
                )
            }
        }
    }

    private fun inspectPdf(file: PlatformFile): LocalBookInspection = withDescriptor(file) { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val pages = (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    OfflineBookPage(
                        bookId = KomgaBookId(""),
                        fileName = "page-${index + 1}.jpg",
                        mediaType = "image/jpeg",
                        width = page.width,
                        height = page.height,
                        fileSize = null,
                    )
                }
            }
            require(pages.isNotEmpty()) { "PDF contains no pages" }
            LocalBookInspection(
                mediaType = "application/pdf",
                mediaProfile = MediaProfile.PDF,
                pages = pages,
                thumbnail = AndroidPdfExtractor(FileKit.context).getPageBytes(
                    file = file,
                    pageNumber = 1,
                    preferredWidth = pages.first().width,
                    preferredHeight = pages.first().height,
                ),
            )
        }
    }

    private suspend fun <T> withZip(file: LocalLibraryFile, block: (ZipFile) -> T): T {
        val caller = currentCoroutineContext()
        return runInterruptible(Dispatchers.IO) {
            zipOpener.open(file.file, file.sizeBytes.takeIf { it > 0 }, caller::ensureActive).use {
                block(it.zip)
            }
        }
    }

    private fun <T> withDescriptor(file: PlatformFile, block: (ParcelFileDescriptor) -> T): T {
        val descriptor = when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> ParcelFileDescriptor.open(androidFile.file, ParcelFileDescriptor.MODE_READ_ONLY)
            is AndroidFile.UriWrapper -> FileKit.context.contentResolver.openFileDescriptor(androidFile.uri, "r")
                ?: error("Cannot open ${androidFile.uri}")
        }
        return descriptor.use(block)
    }
}
