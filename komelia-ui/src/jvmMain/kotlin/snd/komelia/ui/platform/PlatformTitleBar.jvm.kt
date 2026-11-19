package snd.komelia.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.jetbrains.JBR
import snd.komelia.DesktopPlatform
import snd.komelia.DesktopPlatform.Linux
import snd.komelia.DesktopPlatform.MacOS
import snd.komelia.DesktopPlatform.Unknown
import snd.komelia.DesktopPlatform.Windows
import snd.komelia.ui.LocalAwtWindowState
import snd.komelia.ui.LocalWindow
import snd.komelia.ui.MacDesign
import snd.komelia.ui.LocalMacTitleBarCenterBias

val csdEnvEnabled = System.getenv("USE_CSD")?.toBoolean() ?: true
actual fun canIntegrateWithSystemBar() = JBR.isAvailable() && csdEnvEnabled

@Composable
actual fun PlatformTitleBar(
    modifier: Modifier,
    applyInsets: Boolean,
    content: @Composable TitleBarScope.() -> Unit,
) {
    val window = LocalWindow.current
    val windowState = LocalAwtWindowState.current

    if (DesktopPlatform.Current == MacOS) {
        val leadingInset = if (windowState.placement == WindowPlacement.Fullscreen) 12.dp else 112.dp
        TitleBarLayout(
            modifier = modifier.heightIn(min = if (windowState.placement == WindowPlacement.Fullscreen) 0.dp else MacDesign.toolbarHeight),
            applyTitleBar = { PaddingValues(start = leadingInset, end = 8.dp) },
            onElementsPlaced = {},
            centerBias = LocalMacTitleBarCenterBias.current,
            content = content,
        )
        return
    }

    if (!canIntegrateWithSystemBar() || windowState.placement == WindowPlacement.Fullscreen) {
        SimpleTitleBarLayout(modifier, applyInsets, content)
    } else {
        when (DesktopPlatform.Current) {
            Windows -> TitleBarOnWindows(
                modifier.heightIn(min = 32.dp).background(MaterialTheme.colorScheme.surface),
                window,
                content
            )

            Linux -> TitleBarOnLinux(
                modifier.heightIn(min = 32.dp).background(MaterialTheme.colorScheme.surface),
                window = window,
                windowState = windowState,
                content = content
            )

            MacOS, Unknown -> SimpleTitleBarLayout(modifier, applyInsets, content)
        }
    }
}
