package snd.komelia.offline.mediacontainer.divina

import io.github.vinceglb.filekit.PlatformFile
import snd.komelia.offline.mediacontainer.DivinaExtractor

class DivinaRarExtractor(private val rarExtractor: RarExtractor) : DivinaExtractor {
    override fun mediaTypes(): List<String> = listOf(
        "application/x-rar-compressed",
        "application/vnd.rar",
        "application/rar",
        "application/x-cbr"
    )

    override fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray {
        return rarExtractor.getEntryBytes(file, entryName)
    }

    override fun getEntryBytes(file: PlatformFile, entryName: String, pageNumber: Int): ByteArray {
        return rarExtractor.getEntryBytes(file, entryName, pageNumber)
    }
}
