package snd.komelia.offline.local

import snd.komelia.offline.library.model.OfflineLibrary
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.library.ScanInterval
import snd.komga.client.library.SeriesCover
import snd.komga.client.series.KomgaSeriesId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalLibraryIdentityTest {
    @Test
    fun identifiesOnlyLocalLibraryIds() {
        assertTrue(KomgaLibraryId("local-library-device-books").isLocalLibrary())
        assertFalse(KomgaLibraryId("remote-library").isLocalLibrary())
        assertFalse(KomgaLibraryId("my-local-library-copy").isLocalLibrary())
    }

    @Test
    fun identifiesOnlyLocalSeriesIds() {
        assertTrue(KomgaSeriesId("local-series-device-books").isLocalSeries())
        assertFalse(KomgaSeriesId("remote-series").isLocalSeries())
    }

    @Test
    fun identifiesOnlyLocalBookIds() {
        assertTrue(KomgaBookId("local-book-device-book").isLocalBook())
        assertFalse(KomgaBookId("remote-book").isLocalBook())
    }

    @Test
    fun sourceClassificationRejectsLegacyLocalIdsFromRemoteDownloads() {
        assertTrue(library("local-library-device-books", "remote-server").isLocalSourceLibrary())
        assertTrue(library("device-books", LOCAL_SERVER_ID.value).isLocalSourceLibrary())
        assertFalse(library("remote-library", "remote-server").isLocalSourceLibrary())
    }

    private fun library(id: String, serverId: String) = OfflineLibrary(
        id = KomgaLibraryId(id),
        mediaServerId = OfflineMediaServerId(serverId),
        name = id,
        root = "/books",
        importComicInfoBook = false,
        importComicInfoSeries = false,
        importComicInfoCollection = false,
        importComicInfoReadList = false,
        importComicInfoSeriesAppendVolume = false,
        importEpubBook = true,
        importEpubSeries = true,
        importMylarSeries = false,
        importLocalArtwork = false,
        importBarcodeIsbn = false,
        scanForceModifiedTime = false,
        scanInterval = ScanInterval.DISABLED,
        scanOnStartup = false,
        scanCbx = true,
        scanPdf = true,
        scanEpub = true,
        scanDirectoryExclusions = emptyList(),
        repairExtensions = false,
        convertToCbz = false,
        emptyTrashAfterScan = false,
        seriesCover = SeriesCover.FIRST,
        hashFiles = false,
        hashPages = false,
        hashKoreader = false,
        analyzeDimensions = false,
        oneshotsDirectory = null,
        unavailable = false,
    )
}
