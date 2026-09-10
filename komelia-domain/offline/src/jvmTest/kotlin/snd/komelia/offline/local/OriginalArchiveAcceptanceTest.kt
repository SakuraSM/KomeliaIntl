package snd.komelia.offline.local

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.apache.commons.compress.archivers.zip.ZipFile
import snd.komelia.offline.mediacontainer.ArchiveScratchSpace
import snd.komelia.offline.mediacontainer.ComicArchiveExtractor
import snd.komelia.offline.mediacontainer.ComicArchiveService
import snd.komelia.offline.mediacontainer.fileArchiveSource
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OriginalArchiveAcceptanceTest {
    @Test fun suppliedOriginalImportsAndReadsAllPagesOnDesktop() = runBlocking {
        val originalPath = System.getenv("KOMELIA_QA_ORIGINAL_ARCHIVE")
        val referencePath = System.getenv("KOMELIA_QA_REFERENCE_ARCHIVE")
        assumeTrue("Optional local acceptance fixtures were not supplied", originalPath != null && referencePath != null)
        val original = File(originalPath!!)
        val reference = File(referencePath!!)
        val before = original.readBytes()
        val platform = checkNotNull(createLocalLibraryPlatform())
        val inspection = platform.inspect(LocalLibraryFile(PlatformFile(original), original.name, original.name, original.length(), original.lastModified()))
        assertEquals("application/x-7z-compressed", inspection.mediaType)
        assertEquals(59, inspection.pages.size)
        val scratch = Files.createTempDirectory("original-acceptance-").toFile()
        try {
            ComicArchiveService(ArchiveScratchSpace(scratch)).use { service ->
                val extractor = ComicArchiveExtractor(service) { fileArchiveSource(it.file) }
                ZipFile.builder().setFile(reference).get().use { zip ->
                    for (page in inspection.pages.reversed()) {
                        val expected = zip.getInputStream(zip.getEntry(page.fileName)).use { it.readBytes() }
                        assertContentEquals(expected, extractor.getEntryBytes(PlatformFile(original), page.fileName))
                    }
                }
            }
            assertContentEquals(before, original.readBytes())
        } finally { scratch.deleteRecursively() }
    }
}
