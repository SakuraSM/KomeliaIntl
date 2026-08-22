package snd.komelia.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import snd.komelia.ui.common.cards.CoverCaptionVariant
import snd.komelia.ui.common.cards.coverCaptionHeight
import snd.komelia.ui.common.cards.metadataTagLimit
import snd.komelia.ui.common.cards.summarizeMetadataTags
import snd.komelia.ui.home.calculateHomeGroupOverflowLayout
import snd.komelia.ui.home.HomeGroupOverflowLayout
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
        assertEquals(12.dp, layout.pageVerticalPadding)
        assertEquals(12.dp, layout.gridSpacing)
        assertEquals(16.dp, layout.sectionSpacing)
        assertEquals(12.dp, layout.itemSpacing)
        assertEquals(8.dp, layout.controlSpacing)
        assertEquals(12.dp, layout.cardContentPadding)
        assertEquals(16.dp, layout.dialogContentPadding)
        assertEquals(48.dp, layout.minimumTouchTarget)
    }

    @Test
    fun responsiveLayoutSpecsFollowTheFourDpSpacingRhythm() {
        val medium = komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.MEDIUM)
        val expanded = komeliaLayoutSpec(PlatformType.DESKTOP, WindowSizeClass.EXPANDED)
        val full = komeliaLayoutSpec(PlatformType.DESKTOP, WindowSizeClass.FULL)

        assertEquals(listOf(16.dp, 16.dp, 16.dp, 20.dp, 12.dp, 8.dp, 16.dp, 20.dp), medium.spacingValues())
        assertEquals(listOf(24.dp, 20.dp, 16.dp, 24.dp, 12.dp, 8.dp, 16.dp, 24.dp), expanded.spacingValues())
        assertEquals(listOf(24.dp, 24.dp, 16.dp, 24.dp, 12.dp, 8.dp, 16.dp, 24.dp), full.spacingValues())
        assertEquals(48.dp, medium.minimumTouchTarget)
        assertEquals(40.dp, expanded.minimumTouchTarget)
        assertEquals(1200.dp, full.contentMaxWidth)
    }

    @Test
    fun homeGroupsHideMoreWhenEverythingFits() {
        val result = calculateHomeGroupOverflowLayout(
            availableWidth = 420,
            allChipWidth = 60,
            moreChipWidth = 72,
            groupWidths = listOf(80, 90, 100),
            activeGroupIndex = 1,
            spacing = 8,
        )

        assertEquals(listOf(0, 1, 2), result.visibleGroupIndices)
        assertEquals(emptyList(), result.overflowGroupIndices)
    }

    @Test
    fun homeGroupsKeepAStablePrefixAndOverflowOrder() {
        val result = calculateHomeGroupOverflowLayout(
            availableWidth = 300,
            allChipWidth = 60,
            moreChipWidth = 72,
            groupWidths = listOf(70, 80, 90, 100),
            activeGroupIndex = null,
            spacing = 8,
        )

        assertEquals(listOf(0), result.visibleGroupIndices)
        assertEquals(listOf(1, 2, 3), result.overflowGroupIndices)
    }

    @Test
    fun activeOverflowGroupIsPromotedAndDisplacedGroupReturnsToMore() {
        val result = calculateHomeGroupOverflowLayout(
            availableWidth = 320,
            allChipWidth = 60,
            moreChipWidth = 72,
            groupWidths = listOf(70, 80, 90, 240),
            activeGroupIndex = 3,
            spacing = 8,
        )

        assertEquals(listOf(3), result.visibleGroupIndices)
        assertEquals(listOf(0, 1, 2), result.overflowGroupIndices)
    }

    @Test
    fun invalidOrEmptyGroupsNeverDestabilizeTheOverflowResult() {
        assertEquals(
            HomeGroupOverflowLayout(emptyList(), emptyList()),
            calculateHomeGroupOverflowLayout(320, 60, 72, emptyList(), 4, 8),
        )
        val result = calculateHomeGroupOverflowLayout(220, 60, 72, listOf(400, 70), 99, 8)
        assertEquals(emptyList(), result.visibleGroupIndices)
        assertEquals(listOf(0, 1), result.overflowGroupIndices)
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

private fun KomeliaLayoutSpec.spacingValues() = listOf(
    pageHorizontalPadding,
    pageVerticalPadding,
    gridSpacing,
    sectionSpacing,
    itemSpacing,
    controlSpacing,
    cardContentPadding,
    dialogContentPadding,
)
