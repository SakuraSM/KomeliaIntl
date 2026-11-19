package snd.komelia.ui.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import snd.komelia.settings.model.EpubDisplaySettings
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalMacOS
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.BackPressHandler
import snd.webview.KomeliaWebview
import snd.webview.compose.Webview

@Composable
fun EpubContent(
    onWebviewCreated: (KomeliaWebview) -> Unit,
    onBackButtonPress: () -> Unit,
    contentReady: Boolean,
    displaySettings: EpubDisplaySettings,
    backgroundColor: Color,
) {
    val mobile = LocalPlatform.current == PlatformType.MOBILE
    val mac = LocalMacOS.current
    // Fullscreen controls system-bar visibility, not permission to cover a display cutout.
    val insets = if (mobile) WindowInsets.safeDrawing else WindowInsets(0)
    Box(
        Modifier.fillMaxSize()
            .background(if (mac) MaterialTheme.colorScheme.background else backgroundColor)
            .windowInsetsPadding(insets)
            .padding(top = if (mobile) displaySettings.topSpacingDp.dp else 0.dp)
    ) {
        Box(
            if (mac) Modifier.align(Alignment.Center).padding(vertical = 16.dp)
                .widthIn(max = 960.dp).fillMaxWidth().fillMaxHeight().background(backgroundColor)
            else Modifier.fillMaxSize()
        ) {
            Webview(onWebviewCreated)
            if (!contentReady) LoadingMaxSizeIndicator(Modifier.background(backgroundColor))
        }
    }
    BackPressHandler(onBackButtonPress)
}
