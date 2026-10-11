package snd.komelia.sharing

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import snd.komelia.image.PageShareService
import snd.komelia.image.SharedPageImage

private const val SHARE_DIRECTORY = "shared-pages"
private const val SHARE_FILE_PREFIX = "komelia-page-"
private const val SHARE_RETENTION_MILLIS = 24 * 60 * 60 * 1000L

class PageShareFileProvider : FileProvider()

internal class AndroidPageShareService(
    private val context: Context,
    private val activity: StateFlow<Activity?>,
) : PageShareService {
    override suspend fun share(image: SharedPageImage): Unit = withContext(Dispatchers.IO) {
        val prepared = prepareSharedPage(context, image)
        var launched = false
        try {
            withContext(Dispatchers.Main.immediate) {
                val currentActivity = activity.value?.takeUnless { it.isFinishing || it.isDestroyed }
                    ?: throw IOException("No active reader window")
                currentActivity.startActivity(Intent.createChooser(pageShareIntent(context, prepared), null))
                launched = true
            }
        } finally {
            if (!launched) prepared.file.delete()
        }
    }
}

internal data class PreparedSharedPage(val file: File, val mediaType: String)

internal fun prepareSharedPage(context: Context, image: SharedPageImage): PreparedSharedPage {
    require(image.pageNumber >= 1)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(image.encodedImage, 0, image.encodedImage.size, options)
    val mediaType = options.outMimeType?.takeIf { it.startsWith("image/") }
    if (options.outWidth <= 0 || options.outHeight <= 0 || mediaType == null) {
        throw IOException("The page does not contain a supported image")
    }
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mediaType) ?: "img"
    val directory = File(context.cacheDir, SHARE_DIRECTORY)
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not prepare the share cache")
    val cutoff = System.currentTimeMillis() - SHARE_RETENTION_MILLIS
    directory.listFiles()?.filter {
        it.isFile && it.name.startsWith(SHARE_FILE_PREFIX) && it.lastModified() < cutoff
    }?.forEach { it.delete() }
    val file = File.createTempFile("$SHARE_FILE_PREFIX${image.pageNumber}-", ".$extension", directory)
    try {
        file.outputStream().use { it.write(image.encodedImage) }
        return PreparedSharedPage(file, mediaType)
    } catch (error: Throwable) {
        file.delete()
        throw error
    }
}

internal fun pageShareIntent(context: Context, page: PreparedSharedPage): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.page-share", page.file)
    return Intent(Intent.ACTION_SEND).apply {
        type = page.mediaType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
