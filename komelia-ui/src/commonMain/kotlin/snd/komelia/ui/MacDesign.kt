package snd.komelia.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

internal val LocalMacTitleBarCenterBias = staticCompositionLocalOf { 0f }

/** Geometry for the approved compact macOS layouts. */
internal object MacDesign {
    val sidebarWidth = 258.dp
    val narrowSidebarWidth = 160.dp
    val sidebarInset = 16.dp
    val sidebarTopInset = 32.dp
    val sidebarRowHeight = 48.dp
    val toolbarHeight = 58.dp
    val pageInset = 40.dp
    val heroCoverWidth = 166.dp
    val heroControlsWidth = 400.dp
    val heroActionWidth = 184.dp
    val recentCoverWidth = 150.dp
    val narrowRecentCoverWidth = 105.dp
}
