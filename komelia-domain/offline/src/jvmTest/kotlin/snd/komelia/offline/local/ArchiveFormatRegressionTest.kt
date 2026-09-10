package snd.komelia.offline.local

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchiveFormatRegressionTest {
    @Test fun privateCacheIsNotIndexedWhenItsParentIsSelected() = inDirectory { root ->
        val previousTemporaryDirectory = System.getProperty("java.io.tmpdir")
        try {
            System.setProperty("java.io.tmpdir", root.path)
            root.resolve("user.cbz").writeText("candidate only")
            root.resolve("komelia/comic-archives").apply { mkdirs() }.resolve("cached.zip").writeText("owned cache")
            val files = runBlocking { checkNotNull(createLocalLibraryPlatform()).listSupportedFiles(root.path) }
            assertEquals(listOf("user.cbz"), files.map { it.displayName })
        } finally { System.setProperty("java.io.tmpdir", previousTemporaryDirectory) }
    }

    @Test fun discoversSevenZipComicExtensions() {
        assertTrue(isSupportedLocalBook("book.7z"))
        assertTrue(isSupportedLocalBook("book.CB7"))
    }

    @Test fun importsSevenZipRenamedToCbzWithoutChangingItsBytes() = inDirectory { root ->
        val file = root.resolve("chapter.cbz")
        SevenZOutputFile(file).use { archive ->
            archive.setContentCompression(SevenZMethod.COPY)
            for (name in listOf("chapter/10.png", "chapter/2.png", "chapter/1.png")) {
                archive.putArchiveEntry(SevenZArchiveEntry().apply { this.name = name })
                archive.write(byteArrayOf(1, 2, 3))
                archive.closeArchiveEntry()
            }
        }
        val before = file.readBytes()
        val result = inspect(file)
        assertEquals("application/x-7z-compressed", result.mediaType)
        assertEquals(listOf("chapter/1.png", "chapter/2.png", "chapter/10.png"), result.pages.map { it.fileName })
        assertContentEquals(before, file.readBytes())
    }

    @Test fun importsZipRenamedToRarByItsContents() = inDirectory { root ->
        val file = root.resolve("chapter.cbr")
        ZipOutputStream(file.outputStream()).use { archive ->
            archive.putNextEntry(ZipEntry("1.png"))
            archive.write(byteArrayOf(1, 2, 3))
            archive.closeEntry()
        }
        assertEquals("application/zip", inspect(file).mediaType)
    }

    @Test fun importsRarRenamedToZipAndReadsTheSameEntry() = inDirectory { root ->
        val encoded = javaClass.getResourceAsStream("/archives/stored-rar4.rar.b64")!!.bufferedReader().use { it.readText() }.trim()
        val file = root.resolve("actually-rar.cbz").apply { writeBytes(java.util.Base64.getDecoder().decode(encoded)) }
        val inspection = inspect(file)
        assertEquals("application/x-rar-compressed", inspection.mediaType)
        assertContentEquals("synthetic rar page\n".toByteArray(), inspection.thumbnail)
    }

    private fun inspect(file: File) = runBlocking {
        checkNotNull(createLocalLibraryPlatform()).inspect(
            LocalLibraryFile(PlatformFile(file), file.name, file.name, file.length(), file.lastModified())
        )
    }

    private fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("archive-format-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
