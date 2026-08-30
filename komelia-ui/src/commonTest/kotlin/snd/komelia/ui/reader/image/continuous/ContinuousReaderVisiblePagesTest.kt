package snd.komelia.ui.reader.image.continuous

import androidx.compose.ui.unit.IntSize
import snd.komelia.ui.reader.image.PageMetadata
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContinuousReaderVisiblePagesTest {
    @Test
    fun layoutWithoutVisiblePagesIsIgnored() {
        assertNull(visiblePageBounds(listOf("series-start", "book-transition")))
    }

    @Test
    fun pageBoundsIgnoreSurroundingLayoutItems() {
        val first = page(1)
        val last = page(2)

        assertEquals(
            VisiblePageBounds(first, last),
            visiblePageBounds(listOf("series-start", first, last, "series-end")),
        )
    }

    private fun page(number: Int) = PageMetadata(
        bookId = KomgaBookId("book-a"),
        pageNumber = number,
        size = IntSize(1200, 1800),
    )
}
