package snd.komelia.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

@Immutable
internal data class KomeliaLayoutSpec(
    val pageHorizontalPadding: Dp,
    val pageVerticalPadding: Dp,
    val gridSpacing: Dp,
    val sectionSpacing: Dp,
    val itemSpacing: Dp,
    val controlSpacing: Dp,
    val cardContentPadding: Dp,
    val dialogContentPadding: Dp,
    val gridBottomPadding: Dp,
    val minimumTouchTarget: Dp,
    val contentMaxWidth: Dp,
)

internal val LocalKomeliaLayout = staticCompositionLocalOf {
    KomeliaLayoutSpec(
        pageHorizontalPadding = 24.dp,
        pageVerticalPadding = 24.dp,
        gridSpacing = 16.dp,
        sectionSpacing = 24.dp,
        itemSpacing = 12.dp,
        controlSpacing = 8.dp,
        cardContentPadding = 16.dp,
        dialogContentPadding = 24.dp,
        gridBottomPadding = 24.dp,
        minimumTouchTarget = 40.dp,
        contentMaxWidth = 1200.dp,
    )
}

internal fun komeliaLayoutSpec(
    platform: PlatformType,
    windowWidth: WindowSizeClass,
): KomeliaLayoutSpec {
    val minimumTouchTarget = if (platform == PlatformType.MOBILE) 48.dp else 40.dp
    return when (windowWidth) {
        WindowSizeClass.COMPACT -> KomeliaLayoutSpec(
            pageHorizontalPadding = 12.dp,
            pageVerticalPadding = 12.dp,
            gridSpacing = 12.dp,
            sectionSpacing = 16.dp,
            itemSpacing = 12.dp,
            controlSpacing = 8.dp,
            cardContentPadding = 12.dp,
            dialogContentPadding = 16.dp,
            gridBottomPadding = 16.dp,
            minimumTouchTarget = minimumTouchTarget,
            contentMaxWidth = 600.dp,
        )

        WindowSizeClass.MEDIUM -> KomeliaLayoutSpec(
            pageHorizontalPadding = 16.dp,
            pageVerticalPadding = 16.dp,
            gridSpacing = 16.dp,
            sectionSpacing = 20.dp,
            itemSpacing = 12.dp,
            controlSpacing = 8.dp,
            cardContentPadding = 16.dp,
            dialogContentPadding = 20.dp,
            gridBottomPadding = 20.dp,
            minimumTouchTarget = minimumTouchTarget,
            contentMaxWidth = 840.dp,
        )

        WindowSizeClass.EXPANDED -> KomeliaLayoutSpec(
            pageHorizontalPadding = 24.dp,
            pageVerticalPadding = 20.dp,
            gridSpacing = 16.dp,
            sectionSpacing = 24.dp,
            itemSpacing = 12.dp,
            controlSpacing = 8.dp,
            cardContentPadding = 16.dp,
            dialogContentPadding = 24.dp,
            gridBottomPadding = 24.dp,
            minimumTouchTarget = minimumTouchTarget,
            contentMaxWidth = 1200.dp,
        )

        WindowSizeClass.FULL -> KomeliaLayoutSpec(
            pageHorizontalPadding = 24.dp,
            pageVerticalPadding = 24.dp,
            gridSpacing = 16.dp,
            sectionSpacing = 24.dp,
            itemSpacing = 12.dp,
            controlSpacing = 8.dp,
            cardContentPadding = 16.dp,
            dialogContentPadding = 24.dp,
            gridBottomPadding = 24.dp,
            minimumTouchTarget = minimumTouchTarget,
            contentMaxWidth = 1200.dp,
        )
    }
}

/** Returns null when the home grid should continue using the configured adaptive card width. */
internal fun posterColumnCount(
    platform: PlatformType,
    windowWidth: WindowSizeClass,
): Int? {
    if (platform != PlatformType.MOBILE) return null
    return when (windowWidth) {
        WindowSizeClass.COMPACT -> 3
        WindowSizeClass.MEDIUM -> 3
        WindowSizeClass.EXPANDED, WindowSizeClass.FULL -> null
    }
}
