package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

class ComicArchiveExtractor(
    private val service: ComicArchiveService,
    private val source: (PlatformFile) -> ArchiveSource,
) : DivinaExtractor {
    override fun mediaTypes() = listOf(
        "application/zip", "application/x-7z-compressed", "application/x-rar-compressed",
        "application/x-rar-compressed; version=4", "application/x-rar-compressed; version=5",
    )

    override fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray =
        getEntryBytes(file, entryName, ::checkArchiveThread)

    override fun getEntryBytes(file: PlatformFile, entryName: String, checkCancelled: () -> Unit): ByteArray =
        service.withArchive(source(file), checkCancelled) { it.readEntryBytes(entryName, checkCancelled) }
}
