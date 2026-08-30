package snd.komelia.ui.common.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PaginationTest {
    @Test
    fun showsEveryPageWhenTheyFit() {
        assertEquals(
            listOf(1, 2, 3, 4).map(PaginationItem::Page),
            paginationItems(totalPages = 4, currentPage = 2, maxSlots = 5),
        )
    }

    @Test
    fun keepsTheBeginningStable() {
        assertEquals(
            listOf(
                PaginationItem.Page(1),
                PaginationItem.Page(2),
                PaginationItem.Page(3),
                PaginationItem.Ellipsis,
                PaginationItem.Page(20),
            ),
            paginationItems(totalPages = 20, currentPage = 1, maxSlots = 5),
        )
    }

    @Test
    fun centersTheCurrentPageWithoutDuplicates() {
        assertEquals(
            listOf(
                PaginationItem.Page(1),
                PaginationItem.Ellipsis,
                PaginationItem.Page(9),
                PaginationItem.Page(10),
                PaginationItem.Page(11),
                PaginationItem.Ellipsis,
                PaginationItem.Page(20),
            ),
            paginationItems(totalPages = 20, currentPage = 10, maxSlots = 7),
        )
    }

    @Test
    fun keepsTheEndStableAndClampsInvalidCurrentPage() {
        assertEquals(
            listOf(
                PaginationItem.Page(1),
                PaginationItem.Ellipsis,
                PaginationItem.Page(18),
                PaginationItem.Page(19),
                PaginationItem.Page(20),
            ),
            paginationItems(totalPages = 20, currentPage = 99, maxSlots = 5),
        )
    }
}
