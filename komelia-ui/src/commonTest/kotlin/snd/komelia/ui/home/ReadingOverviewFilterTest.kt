package snd.komelia.ui.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import snd.komelia.homefilters.BooksHomeScreenFilter
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.search.allOfBooks

class ReadingOverviewFilterTest {
    @Test fun exactReadingQueriesAreRecognizedWithoutDependingOnTranslatedLabels() {
        for (status in listOf(KomgaReadStatus.READ, KomgaReadStatus.IN_PROGRESS)) {
            val filter = BooksHomeScreenFilter.CustomFilter(
                order = 7, label = "My reading group",
                filter = allOfBooks { readStatus { isEqualTo(status) } }.toBookCondition(),
                pageRequest = KomgaPageRequest(sort = KomgaSort.KomgaBooksSort.byReadDateDesc()),
            )
            assertTrue(isReadingOverviewFilter(filter))
            assertFalse(isReadingOverviewFilter(filter.copy(textSearch = "A custom search")))
            assertFalse(isReadingOverviewFilter(filter.copy(filter = allOfBooks {
                readStatus { isEqualTo(status) }
                library { isEqualTo(KomgaLibraryId("specific-library")) }
            }.toBookCondition())))
        }
        assertFalse(isReadingOverviewFilter(BooksHomeScreenFilter.OnDeck(2, "On deck", 20)))
    }
}
