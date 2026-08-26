package snd.komelia.ui.series.view

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeriesPageNavigationStateTest {
    @Test
    fun pageChangeIsSubmittedBeforeAnyLaterScrollWork() {
        val state = SeriesPageNavigationState()
        val submittedPages = mutableListOf<Int>()

        state.request(2, submittedPages::add)

        assertEquals(listOf(2), submittedPages)
        assertEquals(2, state.pendingPage)
    }

    @Test
    fun requestedPageRemainsPendingUntilItsScrollCompletes() {
        val state = SeriesPageNavigationState()
        state.request(3) {}

        assertFalse(state.isRequestedPage(2))
        assertTrue(state.isRequestedPage(3))
        assertEquals(3, state.pendingPage)

        state.onPageScrollCompleted(2)
        assertEquals(3, state.pendingPage)

        state.onPageScrollCompleted(3)

        assertNull(state.pendingPage)
        assertFalse(state.isRequestedPage(3))
    }

    @Test
    fun failedLoadClearsPendingScroll() {
        val state = SeriesPageNavigationState()
        state.request(4) {}

        state.onPageLoadFailed()

        assertNull(state.pendingPage)
        assertFalse(state.isRequestedPage(4))
    }
}
