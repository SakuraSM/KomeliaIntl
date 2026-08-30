package snd.komelia.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderExitControllerTest {
    @Test
    fun rapidRepeatedBackOnlyPerformsOneNavigation() {
        val controller = ReaderExitController()

        assertEquals(ReaderExitAction.Pop, controller.requestExit(canPop = true, hasBook = true))
        assertEquals(ReaderExitAction.Ignore, controller.requestExit(canPop = false, hasBook = true))
    }

    @Test
    fun restoredReaderFallsBackToBookDetailsOnlyOnce() {
        val controller = ReaderExitController()

        assertEquals(ReaderExitAction.RestoreBookDetails, controller.requestExit(canPop = false, hasBook = true))
        assertEquals(ReaderExitAction.Ignore, controller.requestExit(canPop = false, hasBook = true))
    }

    @Test
    fun exitWithoutNavigationTargetIsNotConsumed() {
        val controller = ReaderExitController()

        assertEquals(ReaderExitAction.Ignore, controller.requestExit(canPop = false, hasBook = false))
        assertEquals(ReaderExitAction.Pop, controller.requestExit(canPop = true, hasBook = false))
    }
}
