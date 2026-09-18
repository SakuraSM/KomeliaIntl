package snd.komelia.ui.reader.image.panels

import androidx.compose.ui.unit.IntSize
import snd.komelia.image.ImageRect
import kotlin.test.Test
import kotlin.test.assertEquals

class PanelViewportTest {
    private val panels = PanelsReaderState.PanelData(
        listOf(ImageRect(0, 0, 100, 100), ImageRect(110, 0, 210, 100)), IntSize(220, 320), false,
    )
    private val pages = mapOf(0 to panels, 1 to panels)

    @Test fun predictsNextPanelThenExistingWholePageStep() {
        assertEquals(listOf(UpcomingPanelView(0, 1), UpcomingPanelView(0, null)),
            upcomingPanelViews(PanelsReaderState.PageIndex(0, 0, false), pages, 2))
    }

    @Test fun crossesPageOnlyAfterWholePageStep() {
        assertEquals(listOf(UpcomingPanelView(0, null), UpcomingPanelView(1, 0)),
            upcomingPanelViews(PanelsReaderState.PageIndex(0, 1, false), pages, 2))
        assertEquals(listOf(UpcomingPanelView(1, 0)),
            upcomingPanelViews(PanelsReaderState.PageIndex(0, 1, true), pages, 1))
    }

    @Test fun zeroDisablesBothPredictionAndNeighborLoading() {
        assertEquals(emptyList(), upcomingPanelViews(PanelsReaderState.PageIndex(0, 0, false), pages, 0))
        assertEquals(2..2, panelPreloadRange(2, 10, 0))
    }

    @Test fun capsPredictionsAndRetainsOnlyBoundedPages() {
        assertEquals(2, upcomingPanelViews(PanelsReaderState.PageIndex(0, 0, false), pages, 100).size)
        assertEquals(1..4, panelPreloadRange(2, 10, 2))
        assertEquals(0..1, panelPreloadRange(0, 2, 2))
    }

    @Test fun missingNextPageAndSinglePanelDoNotInventViews() {
        assertEquals(emptyList(), upcomingPanelViews(PanelsReaderState.PageIndex(1, 1, true), pages, 2))
        val singlePanel = panels.copy(panels = panels.panels.take(1), panelCoversMajorityOfImage = true)
        assertEquals(listOf(UpcomingPanelView(1, 0)), upcomingPanelViews(
            PanelsReaderState.PageIndex(0, 0, false), mapOf(0 to singlePanel, 1 to panels), 1))
    }
}
