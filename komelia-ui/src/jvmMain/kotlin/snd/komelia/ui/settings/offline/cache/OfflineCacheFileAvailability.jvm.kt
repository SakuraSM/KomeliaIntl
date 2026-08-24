package snd.komelia.ui.settings.offline.cache

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun isOfflineCacheFileAvailable(file: PlatformFile): Boolean =
    withContext(Dispatchers.IO) { file.file.exists() }
