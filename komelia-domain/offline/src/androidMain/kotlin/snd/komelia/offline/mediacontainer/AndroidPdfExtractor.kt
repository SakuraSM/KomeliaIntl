package snd.komelia.offline.mediacontainer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class AndroidPdfExtractor(private val context: Context) : PdfExtractor {
    override fun getPageBytes(
        file: PlatformFile,
        pageNumber: Int,
        preferredWidth: Int?,
        preferredHeight: Int?,
    ): ByteArray {
        require(pageNumber > 0) { "Page number must be positive" }

        return withDescriptor(file) { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageNumber > renderer.pageCount) throw IndexOutOfBoundsException("Page $pageNumber does not exist")

                renderer.openPage(pageNumber - 1).use { page ->
                    val (targetWidth, targetHeight) = page.targetSize(preferredWidth, preferredHeight)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)

                    val matrix = Matrix().apply {
                        postScale(
                            targetWidth / page.width.toFloat(),
                            targetHeight / page.height.toFloat(),
                        )
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                        bitmap.recycle()
                        output.toByteArray()
                    }
                }
            }
        }
    }

    private fun PdfRenderer.Page.targetSize(preferredWidth: Int?, preferredHeight: Int?): Pair<Int, Int> {
        val rawWidth = preferredWidth?.takeIf { it > 0 } ?: (width * 2)
        val rawHeight = preferredHeight?.takeIf { it > 0 } ?: (height * 2)
        val maxDimension = 4096f
        val scale = minOf(maxDimension / rawWidth, maxDimension / rawHeight, 1f)
        return (rawWidth * scale).roundToInt().coerceAtLeast(1) to
            (rawHeight * scale).roundToInt().coerceAtLeast(1)
    }

    private fun <T> withDescriptor(file: PlatformFile, block: (ParcelFileDescriptor) -> T): T {
        val descriptor = when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> ParcelFileDescriptor.open(androidFile.file, ParcelFileDescriptor.MODE_READ_ONLY)
            is AndroidFile.UriWrapper -> context.contentResolver.openFileDescriptor(androidFile.uri, "r")
                ?: error("Can't open PDF file ${androidFile.uri}")
        }

        return descriptor.use(block)
    }
}
