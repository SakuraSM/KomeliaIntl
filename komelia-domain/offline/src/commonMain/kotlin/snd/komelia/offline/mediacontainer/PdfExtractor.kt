package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile

interface PdfExtractor {
    fun getPageBytes(
        file: PlatformFile,
        pageNumber: Int,
        preferredWidth: Int?,
        preferredHeight: Int?,
    ): ByteArray
}
