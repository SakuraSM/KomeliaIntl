package snd.komelia.ui.settings.offline.cache

import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.context

internal actual fun isOfflineCacheFileAvailable(file: PlatformFile): Boolean {
    return when (val androidFile = file.androidFile) {
        is AndroidFile.FileWrapper -> androidFile.file.exists()
        is AndroidFile.UriWrapper -> runCatching {
            FileKit.context.contentResolver.openFileDescriptor(androidFile.uri, "r")
                ?.use { true }
                ?: false
        }.getOrDefault(false)
    }
}
