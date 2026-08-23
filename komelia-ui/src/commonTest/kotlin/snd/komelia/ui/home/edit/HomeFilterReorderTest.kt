package snd.komelia.ui.home.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import snd.komelia.ui.home.edit.view.filterReorderIndices

class HomeFilterReorderTest {
    @Test
    fun lazyListIndicesExcludeTheDescriptionHeader() {
        assertEquals(0 to 6, filterReorderIndices(1, 7, filterCount = 7))
        assertEquals(6 to 0, filterReorderIndices(7, 1, filterCount = 7))
    }

    @Test
    fun headerAndAddButtonCannotBecomeDragTargets() {
        assertNull(filterReorderIndices(0, 1, filterCount = 7))
        assertNull(filterReorderIndices(1, 8, filterCount = 7))
    }

    @Test
    fun validMovePreservesEveryItemInTheRequestedOrder() {
        assertEquals(
            listOf("B", "C", "D", "E", "F", "G", "A"),
            moveItemSafely(listOf("A", "B", "C", "D", "E", "F", "G"), from = 0, to = 6),
        )
    }

    @Test
    fun staleOrUnchangedIndicesNeverCrashOrMutateTheList() {
        val items = listOf("A", "B", "C")

        assertSame(items, moveItemSafely(items, from = 3, to = 0))
        assertSame(items, moveItemSafely(items, from = -1, to = 0))
        assertSame(items, moveItemSafely(items, from = 1, to = 1))
    }
}
