package snd.komelia.offline.local

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.github.junrar.Archive
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komelia.offline.mediacontainer.AndroidPdfExtractor
import snd.komelia.offline.mediacontainer.SafSeekableReadByteChannel
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.MediaProfile
import java.io.FileInputStream
import kotlin.sequences.asSequence

actual fun createLocalLibraryPlatform(): LocalLibraryPlatform? = AndroidLocalLibraryPlatform()

private class AndroidLocalLibraryPlatform : LocalLibraryPlatform {
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
            "cbr", "rar" -> withRar(file.file) { archive ->
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
            "pdf" -> inspectPdf(file.file)
            else -> error("Unsupported local book: ${file.displayName}")
        }
    }

    private fun collectFiles(document: DocumentFile, prefix: String, output: MutableList<LocalLibraryFile>) {
        document.listFiles().sortedBy { it.name?.lowercase().orEmpty() }.forEach { child ->
            val name = child.name ?: return@forEach
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

    private fun <T> withZip(file: PlatformFile, block: (ZipFile) -> T): T {
        val builder = ZipFile.builder().setUseUnicodeExtraFields(true).setIgnoreLocalFileHeader(true)
        when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> builder.file = androidFile.file
            is AndroidFile.UriWrapper -> builder.setSeekableByteChannel(
                SafSeekableReadByteChannel(androidFile.uri, FileKit.context)
            )
        }
        return builder.get().use(block)
    }

    private fun <T> withRar(file: PlatformFile, block: (Archive) -> T): T = when (val androidFile = file.androidFile) {
        is AndroidFile.FileWrapper -> Archive(androidFile.file).use(block)
        is AndroidFile.UriWrapper -> {
            val descriptor = FileKit.context.contentResolver.openFileDescriptor(androidFile.uri, "r")
                ?: error("Cannot open ${androidFile.uri}")
            descriptor.use { FileInputStream(it.fileDescriptor).use { stream -> Archive(stream).use(block) } }
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
