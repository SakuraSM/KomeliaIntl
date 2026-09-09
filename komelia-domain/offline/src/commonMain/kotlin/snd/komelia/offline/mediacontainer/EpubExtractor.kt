package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

interface EpubExtractor {
    fun prepare(file: PlatformFile) = Unit

    fun prepare(file: PlatformFile, checkCancelled: () -> Unit) {
        checkCancelled()
        prepare(file)
        checkCancelled()
    }

    fun getEntryBytes(file: PlatformFile, entryName: String): ByteArray

    fun getEntryBytes(file: PlatformFile, entryName: String, checkCancelled: () -> Unit): ByteArray {
        checkCancelled()
        return getEntryBytes(file, entryName).also { checkCancelled() }
    }
}
