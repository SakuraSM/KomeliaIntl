package snd.komelia.offline.mediacontainer

import android.content.Context
import io.github.vinceglb.filekit.PlatformFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.Closeable

/** EPUB's ZIP-only reader shares the same provider access and scratch budget as comics. */
class AndroidZipArchiveOpener(
    context: Context,
    scratch: ArchiveScratchSpace = ArchiveScratchSpace.forContext(context),
) {
    private val access = AndroidArchiveSourceAccess(context, scratch)

    fun open(
        file: PlatformFile,
        expectedSize: Long? = null,
        checkCancelled: () -> Unit = ::checkArchiveThread,
    ): OpenedZipArchive {
        val input = access.source(file, expectedSize).open(checkCancelled)
        try {
            val zip = ZipFile.builder().setUseUnicodeExtraFields(true).setIgnoreLocalFileHeader(true)
                .setSeekableByteChannel(input.channel).get()
            return OpenedZipArchive(zip, input)
        } catch (error: Throwable) {
            runCatching { input.close() }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }
}

class OpenedZipArchive(val zip: ZipFile, private val owner: Closeable? = null) : Closeable {
    override fun close() {
        try { zip.close() } finally { owner?.close() }
    }
}
