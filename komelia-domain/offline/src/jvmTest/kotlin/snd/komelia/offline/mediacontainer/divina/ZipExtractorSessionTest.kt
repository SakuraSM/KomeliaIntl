package snd.komelia.offline.mediacontainer.divina

import io.github.vinceglb.filekit.PlatformFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteExisting
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ZipExtractorSessionTest {
    @Test
    fun preparedArchiveIsReusedAcrossEpubResourceRequests() {
        val archive = createTempFile("komelia-epub-session", ".epub")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("OPS/chapter.xhtml"))
            zip.write("chapter".encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OPS/style.css"))
            zip.write("body{}".encodeToByteArray())
            zip.closeEntry()
        }

        val extractor = ZipExtractor()
        val file = PlatformFile(archive.toFile())
        extractor.prepare(file)
        archive.deleteExisting()

        assertContentEquals(
            "chapter".encodeToByteArray(),
            extractor.getEntryBytes(file, "OPS/chapter.xhtml"),
        )
        assertContentEquals(
            "body{}".encodeToByteArray(),
            extractor.getEntryBytes(file, "OPS/style.css"),
        )
    }
}
