package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

class EpubZipExtractor(private val zipExtractor: ZipExtractor) : EpubExtractor {
    override fun prepare(file: PlatformFile) {
        zipExtractor.prepare(file)
    }

    override fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        return zipExtractor.getEntryBytes(file, entryName)
    }

    override fun prepare(file: PlatformFile, checkCancelled: () -> Unit) = zipExtractor.prepare(file, checkCancelled)

    override fun getEntryBytes(file: PlatformFile, entryName: String, checkCancelled: () -> Unit): ByteArray =
        zipExtractor.getEntryBytes(file, entryName, checkCancelled)
}
