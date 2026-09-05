package snd.komelia.ui.reader.image.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderBackActionTest {
    @Test fun pdfBackShowsControlsThenExits() {
        assertEquals(ReaderBackAction.ShowControls, readerBackAction(true, false))
        assertEquals(ReaderBackAction.Exit, readerBackAction(true, true))
    }

    @Test fun hiddenControlsRequireConfirmationAgain() {
        assertEquals(ReaderBackAction.ShowControls, readerBackAction(true, false))
    }

    @Test fun otherImageReadersKeepTheirExistingBackBehavior() {
        assertEquals(ReaderBackAction.HideControls, readerBackAction(false, true))
        assertEquals(ReaderBackAction.Exit, readerBackAction(false, false))
    }
}
