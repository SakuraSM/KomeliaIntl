package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import snd.komelia.settings.model.ContinuousReadingDirection.*
import snd.komelia.ui.reader.image.continuous.continuousFirstPageViewport
import kotlin.test.Test
import kotlin.test.assertEquals

class ContinuousViewportTest {
    @Test fun longWebtoonRequestsOnlyTheVisibleWindow() {
        assertEquals(IntRect(0, 900, 2400, 1980), continuousFirstPageViewport(IntSize(2400,45000), IntSize(2400,1080),900,TOP_TO_BOTTOM))
    }
    @Test fun eitherHorizontalDirectionIsBounded() {
        assertEquals(IntRect(500,0,1580,2400), continuousFirstPageViewport(IntSize(45000,2400),IntSize(1080,2400),500,LEFT_TO_RIGHT))
        assertEquals(IntRect(43420,0,44500,2400), continuousFirstPageViewport(IntSize(45000,2400),IntSize(1080,2400),500,RIGHT_TO_LEFT))
    }
    @Test fun rightToLeftEnteringAfterHeaderOrAnotherPageUsesRightEdge() {
        assertEquals(IntRect(38920,0,40000,2400), continuousFirstPageViewport(IntSize(40000,2400),IntSize(1080,2400),0,RIGHT_TO_LEFT))
        assertEquals(IntRect(39800,0,40000,2400), continuousFirstPageViewport(IntSize(40000,2400),IntSize(200,2400),0,RIGHT_TO_LEFT))
    }
    @Test fun rightToLeftAccountsForSpacingBeforeTheImage() {
        assertEquals(IntRect(43520,0,44600,2400), continuousFirstPageViewport(IntSize(45000,2400),IntSize(1080,2400),500,RIGHT_TO_LEFT,100))
        assertEquals(IntRect(44020,0,45000,2400), continuousFirstPageViewport(IntSize(45000,2400),IntSize(1080,2400),0,RIGHT_TO_LEFT,100))
        assertEquals(IntRect(45000,0,45000,2400), continuousFirstPageViewport(IntSize(45000,2400),IntSize(50,2400),0,RIGHT_TO_LEFT,100))
    }
    @Test fun endOfPageAndShortImagesClampToImageBounds() {
        assertEquals(IntRect(0,950,800,1000), continuousFirstPageViewport(IntSize(800,1000),IntSize(1080,2400),950,TOP_TO_BOTTOM))
        assertEquals(IntRect(0,0,800,1000), continuousFirstPageViewport(IntSize(800,1000),IntSize(1080,2400),0,TOP_TO_BOTTOM))
    }
    @Test fun invalidOrTransientDimensionsCannotProduceInvertedBounds() {
        assertEquals(IntRect.Zero, continuousFirstPageViewport(IntSize(800,15000),IntSize.Zero,0,TOP_TO_BOTTOM))
        assertEquals(IntRect(0,0,800,2400), continuousFirstPageViewport(IntSize(800,15000),IntSize(1080,2400),-20,TOP_TO_BOTTOM))
    }
}
