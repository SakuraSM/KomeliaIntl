package snd.komelia.offline.mediacontainer

import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ComicArchiveServiceTest {
    @Test fun solidLzmaCacheSupportsReverseAndRepeatedEntryReads() = inDirectory { root ->
        val encoded = javaClass.getResourceAsStream("/archives/solid-lzma.7z.b64")!!.bufferedReader().use { it.readText() }.trim()
        val source = root.resolve("solid.cbz").apply { writeBytes(java.util.Base64.getDecoder().decode(encoded)) }
        val conversions = AtomicInteger()
        ComicArchiveService(ArchiveScratchSpace(root.resolve("cache")), onConverted = { conversions.incrementAndGet() }).use { service ->
            repeat(3) {
                service.withArchive(fileArchiveSource(source)) { archive ->
                    assertEquals("synthetic second entry\n", archive.readEntryBytes("two.txt").decodeToString())
                    assertEquals("synthetic first entry\n", archive.readEntryBytes("one.txt").decodeToString())
                }
            }
            assertEquals(1, conversions.get())
        }
    }

    @Test fun readsCopyLzmaAndLzma2WithoutChangingSourceAndConvertsOnlyOnce() = inDirectory { root ->
        for (method in listOf(SevenZMethod.COPY, SevenZMethod.LZMA, SevenZMethod.LZMA2)) {
            val source = sevenZip(root.resolve("$method.cbz"), method)
            val before = source.readBytes()
            val conversions = AtomicInteger()
            val space = ArchiveScratchSpace(root.resolve("cache-$method"))
            ComicArchiveService(space, onConverted = { conversions.incrementAndGet() }).use { service ->
                repeat(3) {
                    service.withArchive(fileArchiveSource(source)) { archive ->
                        assertEquals(ComicArchiveFormat.SEVEN_ZIP, archive.format)
                        for (name in listOf("chapters/2.png", "chapters/1.png")) {
                            assertContentEquals(payload, archive.readEntryBytes(name))
                        }
                    }
                }
                assertEquals(1, conversions.get())
                assertEquals(1, space.cachedFiles().size)
            }
            assertContentEquals(before, source.readBytes())
            ComicArchiveService(space, onConverted = { error("A committed cache must survive reopening") }).use { service ->
                assertContentEquals(payload, read(service, source))
            }
        }
    }

    @Test fun changedSourceInvalidatesCacheAndEvictionRetainsAtMostTwo() = inDirectory { root ->
        val space = ArchiveScratchSpace(root.resolve("cache"))
        val conversions = AtomicInteger()
        ComicArchiveService(space, onConverted = { conversions.incrementAndGet() }).use { service ->
            val source = sevenZip(root.resolve("one.cb7"))
            read(service, source)
            val modified = source.lastModified()
            sevenZip(source, bytes = byteArrayOf(8, 9))
            source.setLastModified(modified + 2_000)
            assertContentEquals(byteArrayOf(8, 9), read(service, source))
            assertEquals(1, space.cachedFiles().size)
            read(service, sevenZip(root.resolve("two.7z")))
            read(service, sevenZip(root.resolve("three.zip")))
            assertEquals(2, space.cachedFiles().size)
            assertEquals(4, conversions.get())
        }
    }

    @Test fun unknownModifiedTimeDoesNotReusePreviousProcessCache() = inDirectory { root ->
        val source = fileArchiveSource(sevenZip(root.resolve("source.cbz"))).copy(modifiedMillis = null)
        val space = ArchiveScratchSpace(root.resolve("cache"))
        val conversions = AtomicInteger()
        repeat(2) {
            ComicArchiveService(space, onConverted = { conversions.incrementAndGet() }).use { service ->
                service.withArchive(source) { archive -> archive.readEntryBytes("chapters/1.png") }
            }
        }
        assertEquals(2, conversions.get())
        assertEquals(1, space.cachedFiles().size)
    }

    @Test fun concurrentRequestsShareOneConversion() = inDirectory { root ->
        val source = sevenZip(root.resolve("source.zip"), SevenZMethod.LZMA2)
        val conversions = AtomicInteger()
        val executor = Executors.newFixedThreadPool(4)
        try {
            ComicArchiveService(ArchiveScratchSpace(root.resolve("cache")), onConverted = { conversions.incrementAndGet() }).use { service ->
                val start = CountDownLatch(1)
                val requests = (1..8).map { executor.submit<ByteArray> { start.await(); read(service, source) } }
                start.countDown()
                requests.forEach { assertContentEquals(payload, it.get(10, TimeUnit.SECONDS)) }
                assertEquals(1, conversions.get())
            }
        } finally { executor.shutdownNow() }
    }

    @Test fun cancellationDuringDecodeDeletesPartsAndCanRetry() = inDirectory { root ->
        val source = sevenZip(root.resolve("source.cbz"))
        val space = ArchiveScratchSpace(root.resolve("cache"))
        ComicArchiveService(space).use { service ->
            var decodeChecks = 0
            assertFailsWith<CancellationException> {
                service.withArchive(fileArchiveSource(source), {
                    if (ArchivePreparation.activeCount.value > 0 && ++decodeChecks == 3) throw CancellationException("synthetic cancellation")
                }) { error("Cancelled conversion must not produce a readable archive") }
            }
            assertEquals(0, ArchivePreparation.activeCount.value)
            assertTrue(space.directory.listFiles().orEmpty().isEmpty())
            assertContentEquals(payload, read(service, source))
        }
    }

    @Test fun corruptPayloadOrHeaderCannotPublishACache() = inDirectory { root ->
        val source = sevenZip(root.resolve("source.cbz"))
        val original = source.readBytes()
        for ((index, bytes) in listOf(
            original.copyOf().apply { this[32] = (this[32].toInt() xor 1).toByte() },
            original.copyOf(original.size - 5),
        ).withIndex()) {
            source.writeBytes(bytes)
            val space = ArchiveScratchSpace(root.resolve("cache-$index"))
            ComicArchiveService(space).use { service -> assertFailure(LocalArchiveFailure.CORRUPT) { read(service, source) } }
            assertTrue(space.directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun encryptionAndUnsupportedCodecHaveDistinctErrors() = inDirectory { root ->
        val original = sevenZip(root.resolve("source.cbz")).readBytes()
        val variants = listOf(
            replaceCopyCoder(original, byteArrayOf(0x01, 0x7e)) to LocalArchiveFailure.UNSUPPORTED_CODEC,
            replaceCopyCoder(original, byteArrayOf(0x24, 0x06, 0xf1.toByte(), 0x07, 0x01, 0x01, 0x00)) to LocalArchiveFailure.ENCRYPTED,
        )
        for ((index, variant) in variants.withIndex()) {
            val source = root.resolve("variant-$index.cbz").apply { writeBytes(variant.first) }
            val space = ArchiveScratchSpace(root.resolve("cache-$index"))
            ComicArchiveService(space).use { service -> assertFailure(variant.second) { read(service, source) } }
            assertTrue(space.directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun entryTotalCountAndMemoryLimitsAreEnforced() = inDirectory { root ->
        val variants = listOf(
            SevenZipLimits(entryBytes = 2, totalBytes = 100) to SevenZMethod.COPY,
            SevenZipLimits(entryBytes = payload.size.toLong(), totalBytes = payload.size.toLong()) to SevenZMethod.COPY,
            SevenZipLimits(entryCount = 1) to SevenZMethod.COPY,
            SevenZipLimits(memoryKiB = 1) to SevenZMethod.LZMA2,
        )
        for ((index, variant) in variants.withIndex()) {
            val source = sevenZip(root.resolve("source-$index.cbz"), variant.second)
            val space = ArchiveScratchSpace(root.resolve("cache-$index"))
            ComicArchiveService(space, variant.first).use { service ->
                assertFailure(if (index == 3) LocalArchiveFailure.MEMORY_LIMIT else LocalArchiveFailure.DECODE_LIMIT) { read(service, source) }
            }
            assertTrue(space.directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun unsafeAndDuplicatePathsAreRejectedWithoutExtractingFiles() = inDirectory { root ->
        val unsafe = listOf("../outside.png", "/absolute.png", "C:/drive.png", "one/../two.png", "./page.png", "one//page.png")
        for ((index, name) in unsafe.withIndex()) {
            val source = sevenZip(root.resolve("unsafe-$index.cbz"), names = listOf(name))
            ComicArchiveService(ArchiveScratchSpace(root.resolve("cache-$index"))).use { service ->
                assertFailure(LocalArchiveFailure.UNSAFE_ENTRY) { read(service, source) }
            }
        }
        val duplicate = sevenZip(root.resolve("duplicate.cbz"), names = listOf("a/b.png", "a\\b.png"))
        ComicArchiveService(ArchiveScratchSpace(root.resolve("duplicates"))).use { service ->
            assertFailure(LocalArchiveFailure.UNSAFE_ENTRY) { read(service, duplicate) }
        }
        assertFalse(root.resolve("outside.png").exists())
    }

    @Test fun outputZipOverheadCountsTowardsDiskBudgetAndLowSpaceCleansUp() = inDirectory { root ->
        val source = sevenZip(root.resolve("source.cbz"))
        val limited = ArchiveScratchSpace(root.resolve("limited"), ArchiveCopyLimits(32, 64, 0)) { 1_000_000 }
        ComicArchiveService(limited).use { service -> assertFailure(LocalArchiveFailure.COPY_LIMIT) { read(service, source) } }
        assertTrue(limited.directory.listFiles().orEmpty().isEmpty())
        val lowSpace = ArchiveScratchSpace(root.resolve("low-space"), ArchiveCopyLimits(1_000, 2_000, 128)) { 129 }
        ComicArchiveService(lowSpace).use { service -> assertFailure(LocalArchiveFailure.LOW_SPACE) { read(service, source) } }
        assertTrue(lowSpace.directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun unrecognizedTypePermissionAndSplitNameAreNotReportedAsZipErrors() = inDirectory { root ->
        val source = root.resolve("unknown.cbz").apply { writeText("not an archive") }
        ComicArchiveService(ArchiveScratchSpace(root.resolve("cache"))).use { service ->
            assertFailure(LocalArchiveFailure.UNKNOWN_FORMAT) { read(service, source) }
            val inaccessible = fileArchiveSource(source).copy(open = { throw SecurityException("synthetic denied") })
            assertFailure(LocalArchiveFailure.PERMISSION) { service.withArchive(inaccessible) { } }
            val multipart = fileArchiveSource(source).copy(displayName = "book.7z.001")
            assertFailure(LocalArchiveFailure.MULTI_VOLUME) { service.withArchive(multipart) { } }
        }
    }

    @Test fun zipEntryMatchingRetainsUrlAndUniqueBasenameCompatibility() = inDirectory { root ->
        val file = root.resolve("actually-zip.7z")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("chapter one/1.png")); zip.write(payload); zip.closeEntry()
        }
        ComicArchiveService(ArchiveScratchSpace(root.resolve("cache"))).use { service ->
            service.withArchive(fileArchiveSource(file)) { archive ->
                assertEquals(ComicArchiveFormat.ZIP, archive.format)
                assertContentEquals(payload, archive.readEntryBytes("chapter%20one/1.png"))
                assertContentEquals(payload, archive.readEntryBytes("1.png"))
            }
        }
    }

    private fun read(service: ComicArchiveService, file: File): ByteArray =
        service.withArchive(fileArchiveSource(file)) { it.readEntryBytes("chapters/1.png") }

    private fun assertFailure(expected: LocalArchiveFailure, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<LocalArchiveAccessException>(block = block).reason)
    }

    private fun sevenZip(file: File, method: SevenZMethod = SevenZMethod.COPY, bytes: ByteArray = payload, names: List<String> = listOf("chapters/1.png", "chapters/2.png")): File {
        SevenZOutputFile(file).use { archive ->
            archive.setContentCompression(method)
            for (name in names) {
                archive.putArchiveEntry(SevenZArchiveEntry().apply { this.name = name })
                archive.write(bytes)
                archive.closeArchiveEntry()
            }
        }
        return file
    }

    /** Change only the coder descriptor of a generated COPY fixture; repair both header CRCs. */
    private fun replaceCopyCoder(original: ByteArray, replacement: ByteArray): ByteArray {
        val prefix = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN)
        val headerOffset = 32 + prefix.getLong(12).toInt()
        val header = original.copyOfRange(headerOffset, original.size)
        val marker = byteArrayOf(0x01, 0x01, 0x00) // one coder, one-byte COPY method ID
        val offset = header.indices.first { index -> index + marker.size <= header.size && header.copyOfRange(index, index + marker.size).contentEquals(marker) }
        val rewritten = header.copyOfRange(0, offset + 1) + replacement + header.copyOfRange(offset + marker.size, header.size)
        val result = original.copyOfRange(0, headerOffset) + rewritten
        val output = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        output.putLong(20, rewritten.size.toLong())
        output.putInt(28, CRC32().apply { update(rewritten) }.value.toInt())
        output.putInt(8, CRC32().apply { update(result, 12, 20) }.value.toInt())
        return result
    }

    private fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("comic-service-test-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    private companion object { val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8) }
}
