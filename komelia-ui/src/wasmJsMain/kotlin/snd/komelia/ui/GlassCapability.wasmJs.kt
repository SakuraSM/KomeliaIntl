package snd.komelia.ui

import androidx.compose.runtime.Composable

// Haze's Skiko renderer chooses its supported optical channels on the active browser backend.
@Composable
actual fun rememberGlassRenderingSupported(): Boolean = true
