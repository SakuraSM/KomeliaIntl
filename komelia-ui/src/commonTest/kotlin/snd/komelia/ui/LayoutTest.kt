package snd.komelia.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import snd.komelia.ui.common.cards.CoverCaptionVariant
import snd.komelia.ui.common.cards.coverCaptionHeight
import snd.komelia.ui.common.cards.metadataTagLimit
import snd.komelia.ui.common.cards.summarizeMetadataTags
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class LayoutTest {
    @Test
    fun compactMobileWidthsUseThreeHomeColumns() {
        listOf(360.dp, 375.dp, 412.dp).forEach { width ->
            assertEquals(
                3,
                posterColumnCount(PlatformType.MOBILE, WindowSizeClass.fromDp(width)),
            )
        }
    }

    @Test
    fun mediumMobileWidthUsesThreeHomeColumns() {
        assertEquals(
            3,
            posterColumnCount(PlatformType.MOBILE, WindowSizeClass.fromDp(600.dp)),
        )
    }

    @Test
    fun largeMobileAndDesktopKeepAdaptiveCardWidth() {
        assertNull(posterColumnCount(PlatformType.MOBILE, WindowSizeClass.fromDp(840.dp)))
        assertNull(posterColumnCount(PlatformType.DESKTOP, WindowSizeClass.COMPACT))
    }

    @Test
    fun compactMobileLayoutUsesComfortableTouchTargetsAndDenseGutters() {
        val layout = komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT)

        assertEquals(12.dp, layout.pageHorizontalPadding)
        assertEquals(12.dp, layout.gridSpacing)
        assertEquals(16.dp, layout.sectionSpacing)
        assertEquals(48.dp, layout.minimumTouchTarget)
    }

    @Test
    fun coverCaptionVariantsKeepStablePageLevelHeights() {
        assertEquals(48.dp, coverCaptionHeight(CoverCaptionVariant.TitleOnly, PlatformType.MOBILE))
        assertEquals(68.dp, coverCaptionHeight(CoverCaptionVariant.TitleWithSupporting, PlatformType.MOBILE))
        assertEquals(52.dp, coverCaptionHeight(CoverCaptionVariant.TitleOnly, PlatformType.DESKTOP))
        assertEquals(72.dp, coverCaptionHeight(CoverCaptionVariant.TitleWithSupporting, PlatformType.DESKTOP))
    }

    @Test
    fun metadataTagsWrapWithinAStableVisibleBudget() {
        val summary = summarizeMetadataTags(
            listOf(" 轻小说 ", "冒险", "异世界", "冒险", "暗黑奇幻"),
            metadataTagLimit(WindowSizeClass.COMPACT),
        )

        assertEquals(listOf("轻小说", "冒险", "异世界"), summary.visible)
        assertEquals(1, summary.hiddenCount)
    }
}
