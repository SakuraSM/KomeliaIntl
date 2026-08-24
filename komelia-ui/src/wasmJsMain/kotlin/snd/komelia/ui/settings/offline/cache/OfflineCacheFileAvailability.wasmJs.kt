package snd.komelia.ui.settings.offline.cache

import io.github.vinceglb.filekit.PlatformFile

internal actual suspend fun isOfflineCacheFileAvailable(file: PlatformFile): Boolean = false
