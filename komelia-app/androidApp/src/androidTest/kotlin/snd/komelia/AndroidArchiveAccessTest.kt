package snd.komelia

import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import snd.komelia.offline.mediacontainer.AndroidZipArchiveOpener
import snd.komelia.offline.mediacontainer.ArchiveCopyLimits
import snd.komelia.offline.mediacontainer.ArchiveScratchSpace
import snd.komelia.offline.mediacontainer.LocalArchiveAccessException
import snd.komelia.offline.mediacontainer.LocalArchiveFailure
import snd.komelia.offline.mediacontainer.ZipExtractor

class AndroidArchiveAccessTest {
    @Test fun readerCopyHonoursRequestCancellationAndReleasesTemporaryFiles() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val context = SafChannelTest().providerContext { pipe(zipBytes()) }
        val reader = ZipExtractor(AndroidZipArchiveOpener(context, ArchiveScratchSpace(scratch)))
        var checks = 0
        reader.use {
            assertThrows(CancellationException::class.java) {
                reader.getEntryBytes(uri("cancelled.cbz"), "page.txt") {
                    if (++checks == 6) throw CancellationException("synthetic request cancellation")
                }
            }
        }
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun normalFileAndSeekableProviderReadWithoutTemporaryCopies() = inDirectory { root ->
        val source = root.resolve("source.cbz").apply { writeBytes(zipBytes()) }
        val scratch = root.resolve("scratch")
        val space = ArchiveScratchSpace(scratch)
        var opens = 0
        val context = SafChannelTest().providerContext {
            opens++
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        ZipExtractor(AndroidZipArchiveOpener(context, space)).use { reader ->
            assertEquals("synthetic page", reader.getEntryBytes(PlatformFile(source), "page.txt").decodeToString())
            assertEquals("synthetic page", reader.getEntryBytes(uri("seekable.cbz"), "page.txt").decodeToString())
        }
        assertEquals(1, opens)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun pipeWithUnknownSizeReadsThroughTheSameBoundedReaderCache() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val bytes = zipBytes()
        var opens = 0
        val context = SafChannelTest().providerContext { opens++; pipe(bytes) }
        val opener = AndroidZipArchiveOpener(context, ArchiveScratchSpace(scratch))
        ZipExtractor(opener).use { reader ->
            val first = uri("first.cbz")
            assertEquals("synthetic page", reader.getEntryBytes(first, "page.txt").decodeToString())
            assertEquals("one capability probe and one fresh stream", 2, opens)
            reader.getEntryBytes(first, "page.txt")
            assertEquals("do not copy again while the same archive is cached", 2, opens)
            reader.getEntryBytes(uri("second.epub"), "page.txt")
            reader.getEntryBytes(uri("third.zip"), "page.txt")
            assertEquals("evicted archives must release their scratch files", 2, scratch.listFiles().orEmpty().size)
        }
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun corruptSeekableZipIsNotRetriedAsAProviderCompatibilityProblem() = inDirectory { root ->
        val source = root.resolve("bad.cbz").apply { writeText("not a zip") }
        val scratch = root.resolve("scratch")
        var opens = 0
        val context = SafChannelTest().providerContext {
            opens++
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        val opener = AndroidZipArchiveOpener(context, ArchiveScratchSpace(scratch))
        assertThrows(IOException::class.java) { opener.open(uri("bad.cbz")) }
        assertEquals(1, opens)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun revokedPermissionDoesNotFallBackOrLeaveFiles() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        var opens = 0
        val context = SafChannelTest().providerContext { opens++; throw SecurityException("synthetic denied") }
        val opener = AndroidZipArchiveOpener(context, ArchiveScratchSpace(scratch))
        assertThrows(SecurityException::class.java) { opener.open(uri("denied.cbz")) }
        assertEquals(1, opens)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun failedZipParsingAfterPipeCopyReleasesItsReservation() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val context = SafChannelTest().providerContext { pipe("not a zip".toByteArray()) }
        val opener = AndroidZipArchiveOpener(context, ArchiveScratchSpace(scratch))
        assertThrows(IOException::class.java) { opener.open(uri("bad-pipe.cbz")) }
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun knownAndUnknownLengthCopiesEnforceThePerFileLimit() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val space = ArchiveScratchSpace(scratch, ArchiveCopyLimits(8, 16, 0)) { 1_000 }
        var opened = false
        val knownFailure = assertThrows(LocalArchiveAccessException::class.java) {
            space.copy(9) { opened = true; ByteArrayInputStream(ByteArray(9)) }
        }
        assertEquals(LocalArchiveFailure.COPY_LIMIT, knownFailure.reason)
        assertFalse(opened)
        assertThrows(LocalArchiveAccessException::class.java) { space.copy(null) { ByteArrayInputStream(ByteArray(9)) } }
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun totalBudgetIncludesOpenArchivesAndIsReleasedOnClose() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val space = ArchiveScratchSpace(scratch, ArchiveCopyLimits(8, 12, 0)) { 1_000 }
        val first = space.copy(6) { ByteArrayInputStream(ByteArray(6)) }
        val second = space.copy(null) { ByteArrayInputStream(ByteArray(6)) }
        try {
            assertThrows(LocalArchiveAccessException::class.java) { space.copy(1) { ByteArrayInputStream(byteArrayOf(1)) } }
            assertEquals(2, scratch.listFiles().orEmpty().size)
            first.close()
            space.copy(4) { ByteArrayInputStream(ByteArray(4)) }.close()
        } finally {
            first.close()
            second.close()
        }
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun lowSpaceFailsBeforeOpeningTheProviderStream() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val space = ArchiveScratchSpace(scratch, ArchiveCopyLimits(8, 16, 128)) { 130 }
        val failure = assertThrows(LocalArchiveAccessException::class.java) {
            space.copy(4) { error("must not open input") }
        }
        assertEquals(LocalArchiveFailure.LOW_SPACE, failure.reason)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun cancellationAndReadFailureCloseStreamsAndDeletePartialCopies() = inDirectory { root ->
        val scratch = root.resolve("scratch")
        val space = ArchiveScratchSpace(scratch, ArchiveCopyLimits(100, 100, 0)) { 1_000 }
        var checks = 0
        var closed = false
        assertThrows(CancellationException::class.java) {
            space.copy(null, { if (++checks == 3) throw CancellationException("synthetic cancellation") }) {
                object : ByteArrayInputStream(ByteArray(8)) {
                    override fun close() { closed = true; super.close() }
                }
            }
        }
        assertTrue(closed)
        val failure = IOException("synthetic I/O failure")
        val actual = assertThrows(IOException::class.java) {
            space.copy(null) {
                object : ByteArrayInputStream(ByteArray(8)) {
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw failure
                }
            }
        }
        assertSame(failure, actual)
        assertTrue(scratch.listFiles().orEmpty().isEmpty())
    }

    @Test fun startupCleanupTouchesOnlyOwnedOrphanNames() = inDirectory { root ->
        val scratch = root.resolve("scratch").apply { mkdir() }
        val orphan = scratch.resolve("archive-orphan.tmp").apply { writeText("old copy") }
        val unrelated = scratch.resolve("keep.txt").apply { writeText("not an archive copy") }
        ArchiveScratchSpace(scratch)
        assertFalse(orphan.exists())
        assertTrue(unrelated.exists())
    }

    private fun uri(name: String) = PlatformFile(Uri.parse("content://qa-channel/$name"))

    private fun pipe(bytes: ByteArray): ParcelFileDescriptor {
        val fds = ParcelFileDescriptor.createPipe()
        Thread {
            // The capability probe intentionally closes its pipe without consuming it.
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(fds[1]).use { it.write(bytes) } }
        }.start()
        return fds[0]
    }

    private fun zipBytes(): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("page.txt"))
            zip.write("synthetic page".toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    private fun inDirectory(block: (File) -> Unit) {
        val root = File.createTempFile("qa-archives-", ".test", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        check(root.delete() && root.mkdir())
        try { block(root) } finally { root.deleteRecursively() }
    }
}
