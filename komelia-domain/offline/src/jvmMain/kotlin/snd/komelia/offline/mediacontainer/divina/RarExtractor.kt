package snd.komelia.offline.mediacontainer.divina

import com.github.junrar.Archive
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream

class RarExtractor {
    fun getEntryBytes(file: PlatformFile, entryName: String, pageNumber: Int? = null): ByteArray {
        return Archive(file.file).use { archive ->
            val headers = archive.fileHeaders.filterNot { it.isDirectory }
            val header = headers.findBestMatch(entryName, pageNumber)
                ?: throw IllegalStateException("rar entry does not exist: $entryName")

            ByteArrayOutputStream().use { output ->
                archive.extractFile(header, output)
                output.toByteArray()
            }
        }
    }

    private fun String.normalizeArchivePath() = replace('\\', '/')
        .trimStart('/')

    private fun List<com.github.junrar.rarfile.FileHeader>.findBestMatch(
        entryName: String,
        pageNumber: Int?,
    ): com.github.junrar.rarfile.FileHeader? {
        val requested = entryName.normalizeArchivePath()
        val requestedBaseName = requested.substringAfterLast('/')

        fun com.github.junrar.rarfile.FileHeader.names(): List<String> {
            return listOfNotNull(fileName, fileNameW)
                .map { it.normalizeArchivePath() }
                .distinct()
        }

        firstOrNull { header -> header.names().any { it == requested } }?.let { return it }
        firstOrNull { header -> header.names().any { it.endsWith("/$requested") } }?.let { return it }

        val basenameMatches = filter { header ->
            header.names().any { it.substringAfterLast('/') == requestedBaseName }
        }
        if (basenameMatches.size == 1) return basenameMatches.single()

        val imageHeaders = filter { header ->
            header.names().any { it.substringAfterLast('/').isSupportedImageName() }
        }
        val ordinal = (pageNumber ?: requestedBaseName.trailingNumber())?.minus(1)
        if (ordinal != null && ordinal in imageHeaders.indices) return imageHeaders[ordinal]

        return null
    }

    private fun String.trailingNumber(): Int? {
        val stem = substringBeforeLast('.', this)
        return stem.takeLastWhile { it.isDigit() }.toIntOrNull()
    }

    private fun String.isSupportedImageName(): Boolean {
        val extension = substringAfterLast('.', "").lowercase()
        return extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "jxl")
    }
}
