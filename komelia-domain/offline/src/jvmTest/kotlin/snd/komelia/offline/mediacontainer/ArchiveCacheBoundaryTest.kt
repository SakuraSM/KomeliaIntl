package snd.komelia.offline.mediacontainer

import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.StandardOpenOption.READ
import java.util.Base64
import kotlin.test.*

class ArchiveCacheBoundaryTest {
    @Test fun reclaimsIdleCacheBeforeReportingACombinedBudgetFailure() = inDirectory { root ->
        val sourceFile = solidSource(root)
        val baseline = ArchiveScratchSpace(root.resolve("baseline"))
        ComicArchiveService(baseline).use { service -> service.withArchive(fileArchiveSource(sourceFile)) { } }
        val outputSize = baseline.cachedFiles().single().length()
        val maximumFile = maxOf(sourceFile.length(), outputSize)
        val space = ArchiveScratchSpace(root.resolve("cache"), ArchiveCopyLimits(maximumFile, sourceFile.length() + outputSize, 0)) { 1_000_000 }
        var conversions = 0
        ComicArchiveService(space, onConverted = { conversions++ }).use { service ->
            service.withArchive(fileArchiveSource(sourceFile)) { }
            val pipeSource = fileArchiveSource(sourceFile).copy(identity = "synthetic-pipe", open = { checkCancelled ->
                val copy = space.copy(sourceFile.length(), checkCancelled) { sourceFile.inputStream() }
                SeekableArchiveInput(Files.newByteChannel(copy.file.toPath(), READ), copy)
            })
            service.withArchive(pipeSource) { assertEquals("synthetic second entry\n", it.readEntryBytes("two.txt").decodeToString()) }
            assertEquals(2, conversions)
            assertEquals(1, space.directory.listFiles().orEmpty().size)
        }
    }

    @Test fun startupAndZipReadsCountInactiveDiskCachesTowardsTheTwoArchiveLimit() = inDirectory { root ->
        val source = solidSource(root)
        val baseline = ArchiveScratchSpace(root.resolve("baseline"))
        ComicArchiveService(baseline).use { service -> service.withArchive(fileArchiveSource(source)) { } }
        val validZip = baseline.cachedFiles().single().readBytes()
        val cacheRoot = root.resolve("cache").apply { mkdir() }
        repeat(3) { index -> cacheRoot.resolve("seven-v1-${index.toString().repeat(24)}-${index.toString().repeat(32)}.zip").writeBytes(validZip) }
        val sentinel = cacheRoot.resolve("keep.txt").apply { writeText("not a cache") }
        val space = ArchiveScratchSpace(cacheRoot)
        ComicArchiveService(space).use { service ->
            assertEquals(2, space.cachedFiles().size)
            val zipSource = root.resolve("regular.cbz").apply { writeBytes(validZip) }
            service.withArchive(fileArchiveSource(zipSource)) { assertEquals(ComicArchiveFormat.ZIP, it.format) }
            assertEquals(1, space.cachedFiles().size)
            assertTrue(sentinel.exists())
        }
    }

    @Test fun providerTimeoutIsAReadFailureNotUserCancellation() = inDirectory { root ->
        val source = fileArchiveSource(solidSource(root)).copy(open = { throw SocketTimeoutException("synthetic provider timeout") })
        ComicArchiveService(ArchiveScratchSpace(root.resolve("cache"))).use { service ->
            val failure = assertFailsWith<LocalArchiveAccessException> { service.withArchive(source) { } }
            assertEquals(LocalArchiveFailure.SOURCE_IO, failure.reason)
        }
    }

    private fun solidSource(root: File): File {
        val encoded = javaClass.getResourceAsStream("/archives/solid-lzma.7z.b64")!!.bufferedReader().use { it.readText() }.trim()
        return root.resolve("source.cbz").apply { writeBytes(Base64.getDecoder().decode(encoded)) }
    }

    private fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("archive-cache-boundary-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
}
