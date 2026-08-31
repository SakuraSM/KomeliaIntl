package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

interface EpubExtractor {
    fun prepare(file: PlatformFile) = Unit

    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray
}
