package snd.komelia.offline.mediacontainer

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import androidx.documentfile.provider.DocumentFile
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption.READ

fun ArchiveScratchSpace.Companion.forContext(context: Context): ArchiveScratchSpace =
    forDirectory(File(context.cacheDir, "komelia-saf-archives"))

/** Probe seekability once; retry only an unsupported seek/unknown length, never a parser failure. */
class AndroidArchiveSourceAccess(
    private val context: Context,
    private val scratch: ArchiveScratchSpace = ArchiveScratchSpace.forContext(context),
) {
    fun source(file: PlatformFile, expectedSize: Long? = null, modifiedMillis: Long? = null): ArchiveSource = when (val source = file.androidFile) {
        is AndroidFile.FileWrapper -> fileArchiveSource(source.file)
        is AndroidFile.UriWrapper -> {
            val document = runCatching { DocumentFile.fromSingleUri(context, source.uri) }.getOrNull()
            ArchiveSource(
                identity = source.uri.toString(),
                displayName = runCatching { document?.name }.getOrNull() ?: source.uri.lastPathSegment.orEmpty(),
                size = expectedSize?.takeIf { it >= 0 } ?: runCatching { document?.length()?.takeIf { it > 0 } }.getOrNull(),
                modifiedMillis = modifiedMillis?.takeIf { it > 0 } ?: runCatching { document?.lastModified()?.takeIf { it > 0 } }.getOrNull(),
                verifyAccess = {
                    context.contentResolver.openFileDescriptor(source.uri, "r")?.use { }
                        ?: throw IOException("File provider returned no archive descriptor")
                },
                open = { checkCancelled ->
                    checkCancelled()
                    val channel = SafSeekableReadByteChannel(source.uri, context)
                    var declaredSize = expectedSize?.takeIf { it >= 0 }
                    val needsCopy = try {
                        val descriptorSize = channel.descriptorSize
                        declaredSize = declaredSize ?: descriptorSize.takeIf { it >= 0 }
                        channel.verifyRandomAccess()
                        channel.position(channel.position())
                        val size = channel.size()
                        size < 0 || (size == 0L && descriptorSize < 0)
                    } catch (error: IOException) {
                        if (generateSequence<Throwable>(error) { it.cause }.any { it is ErrnoException && it.errno == OsConstants.ESPIPE }) true
                        else { channel.close(); throw error }
                    } catch (error: Throwable) {
                        runCatching { channel.close() }.exceptionOrNull()?.let(error::addSuppressed)
                        throw error
                    }
                    if (!needsCopy) SeekableArchiveInput(channel)
                    else {
                        channel.close()
                        val copy = scratch.copy(declaredSize, checkCancelled) {
                            context.contentResolver.openInputStream(source.uri) ?: throw IOException("File provider returned no archive stream")
                        }
                        try { SeekableArchiveInput(Files.newByteChannel(copy.file.toPath(), READ), copy) }
                        catch (error: Throwable) { copy.close(); throw error }
                    }
                },
            )
        }
    }
}
