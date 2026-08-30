package snd.komelia.offline.local

import io.github.vinceglb.filekit.PlatformFile
import snd.komelia.offline.media.model.MediaExtension
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komga.client.book.MediaProfile
import snd.komga.client.library.KomgaLibraryId

const val LOCAL_LIBRARY_ID_PREFIX = "local-library-"

fun KomgaLibraryId.isLocalLibrary(): Boolean = value.startsWith(LOCAL_LIBRARY_ID_PREFIX)

data class LocalLibraryFile(
    val file: PlatformFile,
    val relativePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
)

data class LocalBookInspection(
    val mediaType: String,
    val mediaProfile: MediaProfile,
    val pages: List<OfflineBookPage>,
    val extension: MediaExtension? = null,
    val epubDivinaCompatible: Boolean = false,
    val thumbnail: ByteArray? = null,
)

interface LocalLibraryPlatform {
    val scheduledScanningIsManagedByPlatform: Boolean
        get() = false

    suspend fun listSupportedFiles(root: String): List<LocalLibraryFile>

    suspend fun inspect(file: LocalLibraryFile): LocalBookInspection
}

expect fun createLocalLibraryPlatform(): LocalLibraryPlatform?
