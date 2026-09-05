package snd.komelia.ui.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import snd.komelia.settings.model.EpubDisplaySettings
import snd.komelia.ui.LocalPlatform
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
    val insets = if (mobile && !displaySettings.immersiveMode) WindowInsets.safeDrawing else WindowInsets(0)
    Box(
        Modifier.fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(insets)
            .padding(top = if (mobile) displaySettings.topSpacingDp.dp else 0.dp)
    ) {
        Webview(onWebviewCreated)
        if (!contentReady) {
            LoadingMaxSizeIndicator(
                Modifier.background(backgroundColor)
            )
        }
    }
    BackPressHandler(onBackButtonPress)
}
