package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import snd.komelia.AppNotifications
import snd.komelia.AppWindowState
import snd.komelia.KomgaAuthenticationState
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.sse.KomgaEvent

val LocalViewModelFactory = compositionLocalOf<ViewModelFactory> { error("ViewModel factory is not set") }

internal val LocalFirstScreenReady = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun SignalFirstScreenReady(waitMillis: Long = 0) {
    val onReady = LocalFirstScreenReady.current
    if (onReady != null) LaunchedEffect(onReady, waitMillis) {
        delay(waitMillis)
        onReady()
    }
}

val LocalNotifications = compositionLocalOf<AppNotifications> { error("Toaster is not set") }
val LocalKomgaEvents = compositionLocalOf<SharedFlow<KomgaEvent>> { error("Komga events are not set") }
val LocalKomfIntegration = compositionLocalOf { flowOf(false) }
val LocalKomfMangaBakaIntegration = compositionLocalOf { flowOf(false) }
val LocalKeyEvents = compositionLocalOf<SharedFlow<KeyEvent>> { error("Key events are not set") }
val LocalWindowWidth = compositionLocalOf<WindowSizeClass> { error("Window size is not set") }
val LocalWindowHeight = compositionLocalOf<WindowSizeClass> { error("Window size is not set") }
val LocalPlatform = compositionLocalOf<PlatformType> { error("Platform type is not set") }
/** Desktop presentation choice supplied by the macOS host, never persisted. */
val LocalMacOS = staticCompositionLocalOf { false }
val LocalTheme = compositionLocalOf { Theme.DARK }
val LocalWindowState = compositionLocalOf<AppWindowState> { error("Window state was not initialized") }
val LocalLibraries = compositionLocalOf<StateFlow<List<KomgaLibrary>>> { error("Libraries were not initialized") }
val LocalReloadEvents = staticCompositionLocalOf<SharedFlow<Unit>> { error("Reload event flow was not initialized") }
val LocalBookDownloadEvents =
    staticCompositionLocalOf<SharedFlow<DownloadEvent>?> { error("Book download event flow was not initialized") }
val LocalOfflineAvailable = compositionLocalOf { false }
val LocalOfflineMode = staticCompositionLocalOf<StateFlow<Boolean>> { error("offline mode flow was not initialized") }
val LocalKomgaState = staticCompositionLocalOf<KomgaAuthenticationState> { error("komga state was not initialized") }
internal val LocalAppNavigationController = staticCompositionLocalOf<AppNavigationController> {
    error("App navigation controller was not initialized")
}
