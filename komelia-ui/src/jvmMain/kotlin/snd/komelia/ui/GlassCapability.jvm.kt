package snd.komelia.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberGlassRenderingSupported(): Boolean =
    !System.getProperty("skiko.renderApi", "").equals("SOFTWARE", ignoreCase = true)
