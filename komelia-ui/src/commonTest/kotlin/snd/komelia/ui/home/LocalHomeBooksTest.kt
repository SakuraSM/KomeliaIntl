package snd.komelia.ui.home

import snd.komelia.offline.local.LocalLibraryScanState
import snd.komga.client.common.KomgaSort
import snd.komga.client.library.KomgaLibraryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalHomeBooksTest {
    @Test
    fun `local home sort choices map to stable book queries`() {
        assertEquals(
            KomgaSort.KomgaBooksSort.byLastModifiedDateDesc().orders,
            (LocalHomeBookSort.RECENTLY_ADDED.pageRequest().sort as KomgaSort.KomgaBooksSort).orders,
        )
        assertEquals(
            KomgaSort.KomgaBooksSort.byCreatedDateDesc().orders,
            (LocalHomeBookSort.FILE_MODIFIED.pageRequest().sort as KomgaSort.KomgaBooksSort).orders,
        )
        assertEquals(
            KomgaSort.KomgaBooksSort.byTitle(KomgaSort.Direction.ASC).orders,
            (LocalHomeBookSort.TITLE.pageRequest().sort as KomgaSort.KomgaBooksSort).orders,
        )
        assertEquals(LOCAL_HOME_BOOK_PAGE_SIZE, LocalHomeBookSort.RECENTLY_ADDED.pageRequest().size)
        LocalHomeBookSort.entries.forEach { sort ->
            assertEquals(REMOTE_DOWNLOADED_HOME_BOOK_PAGE_SIZE, remoteDownloadedBooksPageRequest(sort).size)
            assertEquals(
                (sort.pageRequest().sort as KomgaSort.KomgaBooksSort).orders,
                (remoteDownloadedBooksPageRequest(sort).sort as KomgaSort.KomgaBooksSort).orders,
            )
        }
    }

    @Test
    fun `home refreshes only when an active local scan finishes`() {
        val libraryId = KomgaLibraryId("local-library-test")
        val idle = LocalLibraryScanState()
        val scanning = LocalLibraryScanState(scanningLibraryId = libraryId)
        val completed = LocalLibraryScanState(importedBooks = 1)

        assertFalse(didLocalLibraryScanFinish(idle, idle))
        assertFalse(didLocalLibraryScanFinish(idle, scanning))
        assertTrue(didLocalLibraryScanFinish(scanning, completed))
    }
}
