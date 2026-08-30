package snd.komelia.ui.home

import snd.komelia.offline.local.LocalLibraryScanState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort

internal const val LOCAL_HOME_BOOK_PAGE_SIZE = 20
internal const val REMOTE_DOWNLOADED_HOME_BOOK_PAGE_SIZE = 20

internal enum class LocalHomeBookSort {
    RECENTLY_ADDED,
    FILE_MODIFIED,
    TITLE,
}

internal fun LocalHomeBookSort.pageRequest(): KomgaPageRequest = KomgaPageRequest(
    size = LOCAL_HOME_BOOK_PAGE_SIZE,
    sort = when (this) {
        LocalHomeBookSort.RECENTLY_ADDED -> KomgaSort.KomgaBooksSort.byLastModifiedDateDesc()
        LocalHomeBookSort.FILE_MODIFIED -> KomgaSort.KomgaBooksSort.byCreatedDateDesc()
        LocalHomeBookSort.TITLE -> KomgaSort.KomgaBooksSort.byTitle(KomgaSort.Direction.ASC)
    },
)

internal fun didLocalLibraryScanFinish(
    previous: LocalLibraryScanState,
    current: LocalLibraryScanState,
): Boolean = previous.scanningLibraryId != null && current.scanningLibraryId == null

internal fun remoteDownloadedBooksPageRequest(): KomgaPageRequest = KomgaPageRequest(
    size = REMOTE_DOWNLOADED_HOME_BOOK_PAGE_SIZE,
    sort = KomgaSort.KomgaBooksSort.byLastModifiedDateDesc(),
)
