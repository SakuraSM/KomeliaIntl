package snd.komelia

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.*
import org.junit.Test
import snd.komelia.offline.mediacontainer.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AndroidComicArchiveTest {
    @Test fun renamedRarReadsThroughTheSameProviderPath() = inDirectory { root ->
        val encoded = InstrumentationRegistry.getInstrumentation().context.assets.open("archives/stored-rar4.rar.b64")
            .bufferedReader().use { it.readText() }.trim()
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        val context = SafChannelTest().providerContext { pipe(bytes) }
        val space = ArchiveScratchSpace(root.resolve("scratch"))
        val access = AndroidArchiveSourceAccess(context, space)
        ComicArchiveService(space).use { service ->
            service.withArchive(access.source(uri)) { archive ->
                assertEquals(ComicArchiveFormat.RAR, archive.format)
                assertEquals("synthetic rar page\n", archive.readEntryBytes("chapter/1.png").decodeToString())
            }
        }
    }

    @Test fun solidLzmaArchiveReadsBackwardThroughAPipe() = inDirectory { root ->
        val encoded = InstrumentationRegistry.getInstrumentation().context.assets.open("archives/solid-lzma.7z.b64")
            .bufferedReader().use { it.readText() }.trim()
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        val context = SafChannelTest().providerContext { pipe(bytes) }
        val space = ArchiveScratchSpace(root.resolve("cache"))
        val access = AndroidArchiveSourceAccess(context, space)
        ComicArchiveService(space).use { service ->
            service.withArchive(access.source(uri)) { archive ->
                assertEquals("synthetic second entry\n", archive.readEntryBytes("two.txt").decodeToString())
                assertEquals("synthetic first entry\n", archive.readEntryBytes("one.txt").decodeToString())
            }
        }
    }

    @Test fun suppliedOriginalArchiveMatchesEveryReferencePage() = inDirectory { root ->
        val arguments = InstrumentationRegistry.getArguments()
        val originalUri = arguments.getString("qa.originalArchiveUri")
        val referenceUri = arguments.getString("qa.referenceArchiveUri")
        org.junit.Assume.assumeTrue("Optional local acceptance fixture was not supplied", originalUri != null && referenceUri != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val space = ArchiveScratchSpace(root.resolve("cache"))
        val access = AndroidArchiveSourceAccess(context, space)
        ComicArchiveService(space).use { service ->
            val original = access.source(PlatformFile(Uri.parse(originalUri)))
            val reference = access.source(PlatformFile(Uri.parse(referenceUri)))
            service.withArchive(original) { archive ->
                assertEquals(ComicArchiveFormat.SEVEN_ZIP, archive.format)
                val originalPages = archive.entries.associate { it.name to archive.readEntryBytes(it.name) }
                assertEquals(59, originalPages.size)
                service.withArchive(reference) { expected ->
                    assertEquals(originalPages.keys, expected.entries.map { it.name }.toSet())
                    expected.entries.forEach { assertArrayEquals(originalPages.getValue(it.name), expected.readEntryBytes(it.name)) }
                }
            }
        }
    }

    @Test fun seekableProviderAndPipeReadSevenZipUnderCbzWithOneConversion() = inDirectory { root ->
        val archiveFile = sevenZip(root.resolve("source.cbz"))
        for (pipeBacked in listOf(false, true)) {
            val context = SafChannelTest().providerContext {
                if (pipeBacked) pipe(archiveFile.readBytes()) else ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            val space = ArchiveScratchSpace(root.resolve("scratch-$pipeBacked"))
            val access = AndroidArchiveSourceAccess(context, space)
            val conversions = AtomicInteger()
            ComicArchiveService(space, onConverted = { conversions.incrementAndGet() }).use { service ->
                repeat(3) {
                    service.withArchive(access.source(uri, archiveFile.length(), 1)) { archive ->
                        assertEquals(ComicArchiveFormat.SEVEN_ZIP, archive.format)
                        assertArrayEquals(payload, archive.readEntryBytes("chapter/1.png"))
                        assertArrayEquals(payload, archive.readEntryBytes("chapter/2.png"))
                    }
                }
                assertEquals(1, conversions.get())
                assertEquals(1, space.directory.listFiles().orEmpty().size)
                assertFalse(space.directory.listFiles().orEmpty().any { it.extension == "tmp" || it.extension == "part" })
            }
        }
    }

    @Test fun pipeCopyAndConvertedZipShareOneBudget() = inDirectory { root ->
        val archiveFile = sevenZip(root.resolve("source.cbz"))
        val context = SafChannelTest().providerContext { pipe(archiveFile.readBytes()) }
        val sourceSize = archiveFile.length()
        val baseline = ArchiveScratchSpace(root.resolve("baseline"))
        ComicArchiveService(baseline).use { service -> service.withArchive(fileArchiveSource(archiveFile)) { } }
        val convertedSize = baseline.cachedFiles().single().length()
        val perFileLimit = maxOf(sourceSize, convertedSize)
        val space = ArchiveScratchSpace(root.resolve("scratch"), ArchiveCopyLimits(perFileLimit, sourceSize + convertedSize - 1, 0)) { 1_000_000 }
        val access = AndroidArchiveSourceAccess(context, space)
        ComicArchiveService(space).use { service ->
            val error = assertThrows(LocalArchiveAccessException::class.java) { service.withArchive(access.source(uri)) { } }
            assertEquals(LocalArchiveFailure.COPY_LIMIT, error.reason)
        }
        assertTrue(space.directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun lowSpaceAndCancellationReleasePipeCopiesAndParts() = inDirectory { root ->
        val archiveFile = sevenZip(root.resolve("source.cbz"))
        val context = SafChannelTest().providerContext { pipe(archiveFile.readBytes()) }
        val space = ArchiveScratchSpace(root.resolve("scratch"))
        val access = AndroidArchiveSourceAccess(context, space)
        ComicArchiveService(space).use { service ->
            var checks = 0
            assertThrows(CancellationException::class.java) {
                service.withArchive(access.source(uri), {
                    if (ArchivePreparation.activeCount.value > 0 && ++checks == 3) throw CancellationException("synthetic cancellation")
                }) { }
            }
            assertTrue(space.directory.listFiles().orEmpty().isEmpty())
            assertEquals(0, ArchivePreparation.activeCount.value)
        }
        val full = ArchiveScratchSpace(root.resolve("full"), ArchiveCopyLimits(1_000_000, 2_000_000, 128)) { 129 }
        val fullAccess = AndroidArchiveSourceAccess(context, full)
        ComicArchiveService(full).use { service ->
            val error = assertThrows(LocalArchiveAccessException::class.java) { service.withArchive(fullAccess.source(uri)) { } }
            assertEquals(LocalArchiveFailure.LOW_SPACE, error.reason)
        }
        assertTrue(full.directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun revokedPermissionRetiresAnAlreadyPreparedCache() = inDirectory { root ->
        val archiveFile = sevenZip(root.resolve("source.cbz"))
        var permitted = true
        val context = SafChannelTest().providerContext {
            if (!permitted) throw SecurityException("synthetic revoked permission")
            ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val space = ArchiveScratchSpace(root.resolve("scratch"))
        val access = AndroidArchiveSourceAccess(context, space)
        ComicArchiveService(space).use { service ->
            service.withArchive(access.source(uri, archiveFile.length(), 1)) { it.readEntryBytes("chapter/1.png") }
            permitted = false
            val error = assertThrows(LocalArchiveAccessException::class.java) {
                service.withArchive(access.source(uri, archiveFile.length(), 1)) { it.readEntryBytes("chapter/1.png") }
            }
            assertEquals(LocalArchiveFailure.PERMISSION, error.reason)
            assertTrue(space.directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun concurrentReadersAndCacheEvictionKeepNativeHandlesBounded() = inDirectory { root ->
        val archiveFile = sevenZip(root.resolve("source.cbz"))
        val context = SafChannelTest().providerContext { ParcelFileDescriptor.open(archiveFile, ParcelFileDescriptor.MODE_READ_ONLY) }
        val space = ArchiveScratchSpace(root.resolve("scratch"))
        val access = AndroidArchiveSourceAccess(context, space)
        val conversions = AtomicInteger()
        val executor = Executors.newFixedThreadPool(3)
        try {
            ComicArchiveService(space, onConverted = { conversions.incrementAndGet() }).use { service ->
                val jobs = (1..6).map {
                    executor.submit<ByteArray> { service.withArchive(access.source(uri, archiveFile.length(), 1)) { it.readEntryBytes("chapter/2.png") } }
                }
                jobs.forEach { assertArrayEquals(payload, it.get(10, TimeUnit.SECONDS)) }
                assertEquals(1, conversions.get())
                for (name in listOf("two.cb7", "three.7z")) {
                    service.withArchive(access.source(PlatformFile(Uri.parse("content://qa-channel/$name")), archiveFile.length(), 1)) { it.readEntryBytes("chapter/1.png") }
                }
                assertEquals(2, space.cachedFiles().size)
            }
        } finally { executor.shutdownNow() }
    }

    private fun sevenZip(file: File): File {
        SevenZOutputFile(file).use { archive ->
            archive.setContentCompression(SevenZMethod.LZMA2)
            for (name in listOf("chapter/1.png", "chapter/2.png")) {
                archive.putArchiveEntry(SevenZArchiveEntry().apply { this.name = name })
                archive.write(payload)
                archive.closeArchiveEntry()
            }
        }
        return file
    }

    private fun pipe(bytes: ByteArray): ParcelFileDescriptor {
        val descriptors = ParcelFileDescriptor.createPipe()
        Thread { runCatching { ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write(bytes) } } }.start()
        return descriptors[0]
    }

    private fun inDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(), "qa-sevenzip-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    private companion object {
        val uri = PlatformFile(Uri.parse("content://qa-channel/original.cbz"))
        val payload = ByteArray(1024) { (it % 251).toByte() }
    }
}
