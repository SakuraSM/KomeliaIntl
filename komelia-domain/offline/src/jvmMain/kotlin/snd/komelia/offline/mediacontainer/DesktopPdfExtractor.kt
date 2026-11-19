package snd.komelia.offline.mediacontainer

import io.github.vinceglb.filekit.PlatformFile
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.jbig2.JBIG2ImageReaderSpi
import snd.jni.DesktopPlatform
import snd.komelia.offline.local.LocalBookInspection
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.MediaProfile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.spi.IIORegistry
import kotlin.math.roundToInt

class DesktopPdfExtractor internal constructor(
    private val useMacRenderer: Boolean = DesktopPlatform.Current == DesktopPlatform.MacOS,
) : PdfExtractor {
    init {
        // The single-jar desktop build can merge ImageIO service descriptors.
        IIORegistry.getDefaultInstance().registerServiceProvider(JBIG2ImageReaderSpi())
    }
    fun inspect(file: PlatformFile): LocalBookInspection {
        val sizes = if (useMacRenderer) {
            MacPdfNative.pageSizes(file.file.absolutePath).toList().chunked(2).map { it[0] to it[1] }
        } else {
            Loader.loadPDF(file.file).use { document ->
                require(document.numberOfPages > 0) { "PDF contains no pages" }
                (0 until document.numberOfPages).map { pageSize(document.getPage(it)) }
            }
        }
        val pages = sizes.mapIndexed { index, (width, height) ->
            OfflineBookPage(
                bookId = KomgaBookId(""),
                fileName = "page-${index + 1}.jpg",
                mediaType = "image/jpeg",
                width = width,
                height = height,
                fileSize = null,
            )
        }
        return LocalBookInspection(
            mediaType = "application/pdf",
            mediaProfile = MediaProfile.PDF,
            pages = pages,
            thumbnail = getPageBytes(file, 1, pages.first().width, pages.first().height),
        )
    }

    override fun getPageBytes(file: PlatformFile, pageNumber: Int, preferredWidth: Int?, preferredHeight: Int?): ByteArray {
        require(pageNumber > 0) { "Page number must be positive" }
        if (useMacRenderer) {
            val (pageWidth, pageHeight) = MacPdfNative.pageSize(file.file.absolutePath, pageNumber)
            val (width, height) = targetSize(pageWidth, pageHeight, preferredWidth, preferredHeight)
            return MacPdfNative.renderPage(file.file.absolutePath, pageNumber, width, height)
        }
        return Loader.loadPDF(file.file).use { document ->
            if (pageNumber > document.numberOfPages) throw IndexOutOfBoundsException("Page $pageNumber does not exist")
            renderPage(document, pageNumber - 1, preferredWidth, preferredHeight)
        }
    }

    private fun renderPage(document: PDDocument, pageIndex: Int, preferredWidth: Int?, preferredHeight: Int?): ByteArray {
        val (pageWidth, pageHeight) = pageSize(document.getPage(pageIndex))
        val (width, height) = targetSize(pageWidth, pageHeight, preferredWidth, preferredHeight)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.background = Color.WHITE
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, width, height)
            PDFRenderer(document).apply { isSubsamplingAllowed = true }
                .renderPageToGraphics(pageIndex, graphics, width.toFloat() / pageWidth, height.toFloat() / pageHeight)
        } finally {
            graphics.dispose()
        }
        val output = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            ImageIO.createImageOutputStream(output).use { imageOutput ->
                writer.output = imageOutput
                val options = writer.defaultWriteParam.apply {
                    compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = 0.95f
                }
                writer.write(null, IIOImage(image, null, null), options)
            }
        } finally {
            writer.dispose()
            image.flush()
        }
        return output.toByteArray()
    }

    private fun targetSize(pageWidth: Int, pageHeight: Int, preferredWidth: Int?, preferredHeight: Int?): Pair<Int, Int> {
        val rawWidth = preferredWidth?.takeIf { it > 0 } ?: (pageWidth.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val rawHeight = preferredHeight?.takeIf { it > 0 } ?: (pageHeight.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val scale = minOf(4096f / rawWidth, 4096f / rawHeight, 1f)
        val width = (rawWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (rawHeight * scale).roundToInt().coerceAtLeast(1)
        return width to height
    }

    private fun pageSize(page: PDPage): Pair<Int, Int> {
        val box = page.cropBox
        require(box.width.isFinite() && box.height.isFinite() && box.width > 0 && box.height > 0) { "Invalid PDF page size" }
        val rotated = ((page.rotation % 360) + 360) % 360 in setOf(90, 270)
        val width = if (rotated) box.height else box.width
        val height = if (rotated) box.width else box.height
        return width.roundToInt().coerceAtLeast(1) to height.roundToInt().coerceAtLeast(1)
    }
}
