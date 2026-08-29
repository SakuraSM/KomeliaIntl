package snd.komelia.ui.reader

import snd.komelia.ui.BookSiblingsContext
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertNotEquals

class ImageReaderScreenTest {
    @Test
    fun differentBooksHaveDifferentScreenKeys() {
        val first = ImageReaderScreen(
            bookId = KomgaBookId("book-a"),
            bookSiblingsContext = BookSiblingsContext.Series,
        )
        val second = ImageReaderScreen(
            bookId = KomgaBookId("book-b"),
            bookSiblingsContext = BookSiblingsContext.Series,
        )

        assertNotEquals(first.key, second.key)
    }
}
