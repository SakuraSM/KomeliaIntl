package snd.komelia.ui

import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.offline.local.isLocalBook
import snd.komelia.offline.local.isLocalSeries
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

internal fun shouldUseOfflineContentApi(
    bookLibraryId: KomgaLibraryId?,
    seriesLibraryId: KomgaLibraryId?,
    bookId: KomgaBookId? = null,
    seriesId: KomgaSeriesId? = null,
): Boolean =
    bookLibraryId?.isLocalLibrary() == true ||
            seriesLibraryId?.isLocalLibrary() == true ||
            bookId?.isLocalBook() == true ||
            seriesId?.isLocalSeries() == true
