package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile
import snd.komelia.offline.book.model.OfflineBook
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komelia.offline.media.model.OfflineMedia
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.book.MediaProfile
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class BookContentExtractorsTest {
    @Test
    fun `pdf page is rendered from the downloaded file`() {
        val expected = byteArrayOf(1, 2, 3)
        val pdfExtractor = RecordingPdfExtractor(expected)
        val (book, media) = pdfBookAndMedia()
        val extractors = BookContentExtractors(
            divinaExtractors = emptyList(),
            epubExtractor = null,
            pdfExtractor = pdfExtractor,
        )

        val actual = extractors.getBookPage(book, media, page = 2)

        assertContentEquals(expected, actual)
        assertEquals(book.fileDownloadPath, pdfExtractor.file)
        assertEquals(2, pdfExtractor.pageNumber)
        assertEquals(900, pdfExtractor.preferredWidth)
        assertEquals(1300, pdfExtractor.preferredHeight)
    }

    @Test
    fun `pdf page fails with a supported error when the platform has no renderer`() {
        val (book, media) = pdfBookAndMedia()
        val extractors = BookContentExtractors(
            divinaExtractors = emptyList(),
            epubExtractor = null,
            pdfExtractor = null,
        )

        val error = assertFailsWith<IllegalStateException> {
            extractors.getBookPage(book, media, page = 1)
        }

        assertEquals("PDF content is not supported", error.message)
    }

    private fun pdfBookAndMedia(): Pair<OfflineBook, OfflineMedia> {
        val bookId = KomgaBookId("book")
        val instant = Instant.fromEpochSeconds(0)
        val book = OfflineBook(
            id = bookId,
            seriesId = KomgaSeriesId("series"),
            libraryId = KomgaLibraryId("library"),
            name = "offline.pdf",
            number = 1,
            deleted = false,
            fileHash = "hash",
            oneshot = false,
            url = "",
            size = "1 MiB",
            sizeBytes = 1,
            created = instant,
            lastModified = instant,
            remoteFileLastModified = instant,
            localFileLastModified = instant,
            remoteUnavailable = false,
            fileDownloadPath = PlatformFile(File("offline.pdf")),
        )
        val media = OfflineMedia(
            bookId = bookId,
            status = KomgaMediaStatus.READY,
            mediaType = "application/pdf",
            mediaProfile = MediaProfile.PDF,
            comment = "",
            epubDivinaCompatible = false,
            pageCount = 2,
            pages = listOf(
                OfflineBookPage(
                    bookId = bookId,
                    fileName = "1",
                    mediaType = "image/jpeg",
                    width = 800,
                    height = 1200,
                    fileSize = null,
                ),
                OfflineBookPage(
                    bookId = bookId,
                    fileName = "2",
                    mediaType = "image/jpeg",
                    width = 900,
                    height = 1300,
                    fileSize = null,
                ),
            ),
        )
        return book to media
    }

    private class RecordingPdfExtractor(
        private val result: ByteArray,
    ) : PdfExtractor {
        var file: PlatformFile? = null
        var pageNumber: Int? = null
        var preferredWidth: Int? = null
        var preferredHeight: Int? = null

        override fun getPageBytes(
            file: PlatformFile,
            pageNumber: Int,
            preferredWidth: Int?,
            preferredHeight: Int?,
        ): ByteArray {
            this.file = file
            this.pageNumber = pageNumber
            this.preferredWidth = preferredWidth
            this.preferredHeight = preferredHeight
            return result
        }
    }
}
