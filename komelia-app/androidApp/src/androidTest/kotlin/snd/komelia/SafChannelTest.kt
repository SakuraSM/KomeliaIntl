package snd.komelia

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import snd.komelia.offline.mediacontainer.SafSeekableReadByteChannel

class SafChannelTest {
    @Test fun channelIsReadOnlyAndRejectsInvalidPositionsAndUseAfterClose() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("qa-channel-contract-", ".bin", target.cacheDir)
        try {
            file.writeBytes(byteArrayOf(1))
            val context = providerContext { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            val channel = SafSeekableReadByteChannel(Uri.parse("content://qa-channel/book"), context)
            assertThrows(IllegalArgumentException::class.java) { channel.position(-1) }
            assertThrows(java.nio.channels.NonWritableChannelException::class.java) { channel.write(ByteBuffer.allocate(1)) }
            assertThrows(java.nio.channels.NonWritableChannelException::class.java) { channel.truncate(0) }
            channel.position(1)
            assertEquals(0, channel.read(ByteBuffer.allocate(0)))
            channel.close()
            channel.close()
            assertThrows(java.nio.channels.ClosedChannelException::class.java) { channel.read(ByteBuffer.allocate(1)) }
            assertThrows(java.nio.channels.ClosedChannelException::class.java) { channel.position() }
            assertEquals(1L, file.length())
        } finally { file.delete() }
    }

    @Test fun nonSeekableReadMustThrowRatherThanPretendToBeEndOfFile() {
        val pipe = ParcelFileDescriptor.createPipe()
        pipe[1].close()
        val context = providerContext { pipe[0] }
        SafSeekableReadByteChannel(Uri.parse("content://qa-channel/book"), context).use { channel ->
            assertThrows(IOException::class.java) { channel.read(ByteBuffer.allocate(16)) }
        }
    }

    @Test fun normalFileSupportsSeekingAndReportsOnlyRealEof() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("qa-channel-", ".bin", target.cacheDir)
        try {
            file.writeBytes(byteArrayOf(10, 20, 30))
            val context = providerContext { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            SafSeekableReadByteChannel(Uri.parse("content://qa-channel/book"), context).use { channel ->
                assertEquals(3L, channel.size())
                channel.position(1)
                val buffer = ByteBuffer.allocate(1)
                assertEquals(1, channel.read(buffer))
                assertEquals(20, buffer.array()[0].toInt())
                channel.position(3)
                assertEquals(-1, channel.read(ByteBuffer.allocate(1)))
            }
        } finally {
            file.delete()
        }
    }

    internal fun providerContext(open: () -> ParcelFileDescriptor): ContextWrapper {
        val resolver = ContentResolver.wrap(object : ContentProvider() {
                override fun onCreate() = true
                override fun openFile(uri: Uri, mode: String) = open()
                override fun getType(uri: Uri) = "application/octet-stream"
                override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor? = null
                override fun insert(uri: Uri, values: ContentValues?): Uri? = null
                override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
                override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        })
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getContentResolver() = resolver
        }
    }
}
