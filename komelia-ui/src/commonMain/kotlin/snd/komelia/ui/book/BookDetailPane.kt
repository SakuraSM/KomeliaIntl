package snd.komelia.ui.book

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalBookDetailPaneClose = staticCompositionLocalOf<(() -> Unit)?> { null }
