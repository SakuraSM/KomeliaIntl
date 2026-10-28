package snd.komelia.ui.home

import kotlin.time.Instant
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadata
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.book.Media
import snd.komga.client.book.MediaProfile
import snd.komga.client.book.ReadProgress
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

internal fun homeReadingBook(id: String, readTimeMillis: Long = 1_000): KomeliaBook {
    val timestamp = Instant.fromEpochMilliseconds(readTimeMillis)
    return KomeliaBook(
        id = KomgaBookId(id), seriesId = KomgaSeriesId("series"), seriesTitle = "Synthetic series",
        libraryId = KomgaLibraryId("library"), name = id, url = "synthetic://$id", number = 1,
        created = timestamp, lastModified = timestamp, fileLastModified = timestamp, sizeBytes = 20, size = "20 B",
        media = Media(KomgaMediaStatus.READY, "application/zip", 4, "", false, false, MediaProfile.DIVINA),
        metadata = KomgaBookMetadata(id, "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
            false, false, false, false, false, false, false, false, false, timestamp, timestamp),
        readProgress = ReadProgress(2, false, timestamp, "test", "test", timestamp, timestamp),
        deleted = false, fileHash = "", oneshot = false, downloaded = true,
        localFileLastModified = timestamp, remoteFileUnavailable = false,
    )
}
