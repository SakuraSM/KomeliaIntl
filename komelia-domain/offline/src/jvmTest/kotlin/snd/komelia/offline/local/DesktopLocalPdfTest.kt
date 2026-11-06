package snd.komelia.offline.local

import java.nio.file.Files
import java.io.ByteArrayInputStream
import java.awt.Color
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assume.assumeTrue
import snd.jni.DesktopPlatform
import snd.komelia.offline.mediacontainer.DesktopPdfExtractor
import snd.komga.client.book.MediaProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopLocalPdfTest {
    @Test fun indexesPdfPagesAndCreatesACover(): Unit = runBlocking {
        val directory = Files.createTempDirectory("komelia-pdf-index-test")
        try {
            val file = directory.resolve("合成 Synthetic.pdf").toFile()
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle(200f, 100f)))
                val second = PDPage(PDRectangle(120f, 80f)).apply { rotation = 90 }
                document.addPage(second)
                PDPageContentStream(document, second).use { content ->
                    content.setNonStrokingColor(Color.RED)
                    content.addRect(0f, 0f, 120f, 80f)
                    content.fill()
                }
                document.save(file)
            }
            val platform = assertNotNull(createLocalLibraryPlatform())
            val files = platform.listSupportedFiles(directory.toString())
            assertEquals(listOf("合成 Synthetic.pdf"), files.map { it.displayName })
            val inspection = platform.inspect(files.single())
            assertEquals(MediaProfile.PDF, inspection.mediaProfile)
            assertEquals(listOf(200 to 100, 80 to 120), inspection.pages.map { it.width to it.height })
            val cover = ImageIO.read(ByteArrayInputStream(assertNotNull(inspection.thumbnail)))
            assertEquals(200 to 100, cover.width to cover.height)
            val background = Color(cover.getRGB(100, 50))
            assertTrue(background.red > 245 && background.green > 245 && background.blue > 245)
            val extractor = DesktopPdfExtractor()
            assertTrue(ImageIO.getImageReadersByFormatName("JBIG2").hasNext())
            val rotated = ImageIO.read(ByteArrayInputStream(extractor.getPageBytes(files.single().file, 2, 80, 120)))
            assertEquals(80 to 120, rotated.width to rotated.height)
            val ink = Color(rotated.getRGB(40, 60))
            assertTrue(ink.red > 200 && ink.green < 40 && ink.blue < 40)
            val portable = DesktopPdfExtractor(useMacRenderer = false)
            val portableCover = ImageIO.read(ByteArrayInputStream(portable.getPageBytes(files.single().file, 1, 200, 100)))
            assertTrue(Color(portableCover.getRGB(100, 50)).red > 245)
            assertFailsWith<IllegalArgumentException> { extractor.getPageBytes(files.single().file, 0, null, null) }
            assertFailsWith<IndexOutOfBoundsException> { extractor.getPageBytes(files.single().file, 3, null, null) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun macRendererReadsJpeg2000ImagesInsidePdf() {
        assumeTrue(DesktopPlatform.Current == DesktopPlatform.MacOS)
        val root = generateSequence(java.nio.file.Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("third_party/libvips/test/test-suite/images/world.jp2")) }
        val fixtures = listOf("world.jp2" to (800 to 400), "Bretagne2_4.j2k" to (2592 to 1944))
        val file = Files.createTempFile("komelia-jpx-test", ".pdf").toFile()
        try {
            PDDocument().use { document ->
                for ((name, dimensions) in fixtures) {
                    val page = PDPage(PDRectangle(64f, 64f))
                    document.addPage(page)
                    val jpeg2000 = Files.readAllBytes(root.resolve("third_party/libvips/test/test-suite/images/$name"))
                    val image = PDImageXObject(document, ByteArrayInputStream(jpeg2000), COSName.JPX_DECODE, dimensions.first, dimensions.second, 8, PDDeviceRGB.INSTANCE)
                    PDPageContentStream(document, page).use { it.drawImage(image, 0f, 0f, 64f, 64f) }
                }
                document.save(file)
            }
            for (page in 1..fixtures.size) {
                val rendered = ImageIO.read(ByteArrayInputStream(DesktopPdfExtractor().getPageBytes(io.github.vinceglb.filekit.PlatformFile(file), page, 64, 64)))
                val colors = (0 until 64 step 4).flatMap { y -> (0 until 64 step 4).map { x -> rendered.getRGB(x, y) } }.distinct()
                if (colors.size <= 3) {
                    val evidence = java.io.File("build/test-results/pdf-rendering").apply { mkdirs() }
                    file.copyTo(evidence.resolve("jpeg2000.pdf"), overwrite = true)
                    ImageIO.write(rendered, "png", evidence.resolve("page-$page.png"))
                }
                assertTrue(colors.size > 3, "Embedded ${fixtures[page - 1].first} on page $page did not render: ${colors.size} colors")
            }
        } finally {
            file.delete()
        }
    }

    @Test fun rendersPdfWithJbig2Images() {
        // Apache PDFBox test fixture, Apache-2.0:
        // https://github.com/apache/pdfbox/blob/dbcbf225cd7b25c4376ba1032409eeae4d7a6bcd/tools/src/test/resources/input/ImageIOUtil/JBIG2Image.pdf
        val file = Files.createTempFile("komelia-jbig2-test", ".pdf").toFile()
        try {
            javaClass.getResourceAsStream("/fixtures/jbig2-image.pdf")!!.use { file.writeBytes(it.readBytes()) }
            val renderers = if (DesktopPlatform.Current == DesktopPlatform.MacOS) listOf(false, true) else listOf(false)
            for (native in renderers) {
                val bytes = DesktopPdfExtractor(useMacRenderer = native)
                    .getPageBytes(io.github.vinceglb.filekit.PlatformFile(file), 1, 200, 160)
                val image = ImageIO.read(ByteArrayInputStream(bytes))
                val pixels = (0 until 160 step 4).flatMap { y -> (0 until 200 step 4).map { x -> Color(image.getRGB(x, y)).red } }
                assertTrue(pixels.any { it < 60 } && pixels.any { it > 200 }, "JBIG2 content did not render")
            }
        } finally {
            file.delete()
        }
    }
}
