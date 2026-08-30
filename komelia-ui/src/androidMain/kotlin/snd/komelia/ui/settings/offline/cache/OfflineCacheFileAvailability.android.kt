package snd.komelia.ui.settings.offline.cache

import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun isOfflineCacheFileAvailable(file: PlatformFile): Boolean =
    withContext(Dispatchers.IO) {
        when (val androidFile = file.androidFile) {
            is AndroidFile.FileWrapper -> androidFile.file.exists()
            is AndroidFile.UriWrapper -> runCatching {
                FileKit.context.contentResolver.openFileDescriptor(androidFile.uri, "r")
                    ?.use { true }
                    ?: false
            }.getOrDefault(false)
        }
    }
