package snd.komelia.ui.reader.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.BackPressHandler
import snd.webview.KomeliaWebview
import snd.webview.compose.Webview

@Composable
fun EpubContent(
    onWebviewCreated: (KomeliaWebview) -> Unit,
    onBackButtonPress: () -> Unit,
    contentReady: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        Webview(onWebviewCreated)
        if (!contentReady) {
            LoadingMaxSizeIndicator(
                Modifier.background(MaterialTheme.colorScheme.background)
            )
        }
    }
    BackPressHandler(onBackButtonPress)
}
