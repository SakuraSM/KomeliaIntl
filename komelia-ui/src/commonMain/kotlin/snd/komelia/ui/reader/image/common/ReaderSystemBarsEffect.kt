package snd.komelia.ui.reader.image.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import snd.komelia.AppWindowState

@Composable
internal fun ReaderSystemBarsEffect(
    windowState: AppWindowState,
    areControlsVisible: Boolean,
) {
    DisposableEffect(windowState, areControlsVisible) {
        if (areControlsVisible) {
            windowState.setFullscreen(false)
        } else {
            windowState.setFullscreen(true, hideNavigationBar = true)
        }
        onDispose { windowState.setFullscreen(false) }
    }
}
