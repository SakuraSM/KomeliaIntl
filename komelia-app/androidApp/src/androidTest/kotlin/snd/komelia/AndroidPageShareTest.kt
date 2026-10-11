package snd.komelia

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import snd.komelia.image.SharedPageImage
import snd.komelia.sharing.pageShareIntent
import snd.komelia.sharing.prepareSharedPage

class AndroidPageShareTest {
    @Test
    fun currentPageIsCopiedUnchangedAndOnlyItsContentUriIsGranted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        val bytes = try {
            bitmap.eraseColor(Color.RED)
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
        val page = prepareSharedPage(context, SharedPageImage(bytes, 2))
        try {
            val intent = pageShareIntent(context, page)
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.page-share", uri.authority)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            assertTrue(page.file.name.startsWith("komelia-page-2-"))
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, uri.authority!!, File(context.filesDir, "private-placeholder.txt"))
            }
        } finally { page.file.delete() }
    }

    @Test
    fun invalidPageDataCannotBeSharedAsAnImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertThrows(IOException::class.java) {
            prepareSharedPage(context, SharedPageImage("not an image".toByteArray(), 1))
        }
    }
}
