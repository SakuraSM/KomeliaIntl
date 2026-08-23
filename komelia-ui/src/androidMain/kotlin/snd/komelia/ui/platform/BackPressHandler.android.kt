package snd.komelia.ui.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun BackPressHandler(onBackPressed: () -> Unit) {
    BackHandler(onBack = onBackPressed)
}
