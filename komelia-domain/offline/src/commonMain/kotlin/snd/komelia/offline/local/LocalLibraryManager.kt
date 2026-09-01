package snd.komelia.offline.local

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.OfflineRepositories
import snd.komelia.offline.book.model.OfflineBook
import snd.komelia.offline.book.model.OfflineBookMetadata
import snd.komelia.offline.book.model.OfflineThumbnailBook
import snd.komelia.offline.library.model.OfflineLibrary
import snd.komelia.offline.media.model.OfflineMedia
import snd.komelia.offline.series.model.OfflineBookMetadataAggregation
import snd.komelia.offline.series.model.OfflineSeries
import snd.komelia.offline.series.model.OfflineSeriesMetadata
import snd.komelia.offline.server.model.OfflineMediaServer
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komelia.offline.sync.model.OfflineLogEntry.Companion.logError
import snd.komelia.offline.sync.model.OfflineLogEntry.Companion.logInfo
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaThumbnailId
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.library.ScanInterval
import snd.komga.client.library.SeriesCover
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.series.KomgaSeriesStatus
import snd.komga.client.sse.KomgaEvent
import snd.komga.client.sse.KomgaEvent.BookDeleted
import kotlin.math.absoluteValue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private const val LOCAL_SERVER_URL = "local://device"
internal val LOCAL_SERVER_ID = OfflineMediaServerId("local-device")

data class LocalLibraryScanState(
    val scanningLibraryId: KomgaLibraryId? = null,
    val importedBooks: Int = 0,
    val removedBooks: Int = 0,
    val error: String? = null,
)

class LocalLibraryManager(
    private val repositories: OfflineRepositories,
    private val platform: LocalLibraryPlatform,
    private val scope: CoroutineScope,
    private val komgaEvents: MutableSharedFlow<KomgaEvent>? = null,
) {
    private val availableBooksRepository = AvailableBooksRepository(repositories)
    private val scanMutex = Mutex()
    private val mutableScanState = MutableStateFlow(LocalLibraryScanState())
    val scanState: StateFlow<LocalLibraryScanState> = mutableScanState.asStateFlow()
    private var schedulerJob: Job? = null

    suspend fun getLibraries(): List<OfflineLibrary> {
        return repositories.libraryRepository.findAllByMediaServer(LOCAL_SERVER_ID)
            .filter { it.isLocalSourceLibrary() }
    }

    suspend fun prepareLocalMode() {
        ensureLocalServer()
        if (repositories.userRepository.find(OfflineUser.ROOT) == null) {
            repositories.userRepository.save(OfflineUser.ROOT_USER)
        }
    }

    suspend fun getBooks(pageRequest: KomgaPageRequest = KomgaPageRequest(unpaged = true)): Page<KomeliaBook> {
        return getAvailableBooks(AvailableBookSource.LOCAL, pageRequest = pageRequest)
    }

    suspend fun getRemoteDownloadedBooks(
        pageRequest: KomgaPageRequest = KomgaPageRequest(unpaged = true),
    ): Page<KomeliaBook> {
        return getAvailableBooks(AvailableBookSource.DOWNLOADED, pageRequest = pageRequest)
    }

    suspend fun getAvailableBooks(
        source: AvailableBookSource = AvailableBookSource.ALL,
        query: String = "",
        pageRequest: KomgaPageRequest = KomgaPageRequest(unpaged = true),
    ): Page<KomeliaBook> {
        return availableBooksRepository.getBooks(source, query, pageRequest)
    }

    suspend fun addLibrary(
        root: PlatformFile,
        name: String,
        scanInterval: ScanInterval = ScanInterval.HOURLY,
    ): OfflineLibrary {
        prepareLocalMode()
        val rootValue = root.toString()
        getLibraries().firstOrNull { it.root == rootValue }?.let { return it }

        val library = OfflineLibrary(
            id = KomgaLibraryId("$LOCAL_LIBRARY_ID_PREFIX${stableId(rootValue)}"),
            mediaServerId = LOCAL_SERVER_ID,
            name = name.ifBlank { rootValue.substringAfterLast('/').ifBlank { "Local library" } },
            root = rootValue,
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
            scanInterval = scanInterval,
            scanOnStartup = true,
            scanCbx = true,
            scanPdf = true,
            scanEpub = true,
            scanDirectoryExclusions = emptyList(),
            repairExtensions = false,
            convertToCbz = false,
            emptyTrashAfterScan = true,
            seriesCover = SeriesCover.FIRST,
            hashFiles = false,
            hashPages = false,
            hashKoreader = false,
            analyzeDimensions = true,
            oneshotsDirectory = null,
            unavailable = false,
        )
        repositories.libraryRepository.save(library)
        scan(library.id)
        return library
    }

    suspend fun updateScanInterval(libraryId: KomgaLibraryId, interval: ScanInterval) {
        val library = repositories.libraryRepository.get(libraryId)
        check(library.mediaServerId == LOCAL_SERVER_ID) { "Not a local library" }
        repositories.libraryRepository.save(library.copy(scanInterval = interval))
    }

    suspend fun removeLibrary(libraryId: KomgaLibraryId) {
        val library = repositories.libraryRepository.get(libraryId)
        check(library.mediaServerId == LOCAL_SERVER_ID) { "Not a local library" }
        val series = repositories.seriesRepository.findAllByLibraryId(libraryId)
        series.forEach { localSeries ->
            val bookIds = repositories.bookRepository.findAllIdsBySeriesId(localSeries.id)
            repositories.thumbnailBookRepository.deleteByBookIds(bookIds)
            repositories.mediaRepository.delete(bookIds)
            repositories.bookMetadataRepository.delete(bookIds)
            repositories.bookRepository.delete(bookIds)
            repositories.bookMetadataAggregationRepository.delete(localSeries.id)
            repositories.seriesMetadataRepository.delete(localSeries.id)
            repositories.seriesRepository.delete(localSeries.id)
        }
        repositories.libraryRepository.delete(libraryId)
    }

    suspend fun getExcludedBooks(): List<LocalBookExclusion> = getLibraries().flatMap { library ->
        library.scanDirectoryExclusions.mapNotNull { encoded ->
            encoded.removePrefixOrNull(LOCAL_BOOK_EXCLUSION_PREFIX)?.let { relativePath ->
                LocalBookExclusion(
                    libraryId = library.id,
                    libraryName = library.name,
                    relativePath = relativePath,
                    displayName = relativePath.substringAfterLast('/'),
                )
            }
        }
    }

    suspend fun excludeBook(bookId: KomgaBookId) = scanMutex.withLock {
        val book = repositories.bookRepository.get(bookId)
        check(book.libraryId.isLocalLibrary()) { "Only source files from a local library can be excluded" }
        val relativePath = book.url.removePrefix("local://")
        val library = repositories.libraryRepository.get(book.libraryId)
        val encoded = "$LOCAL_BOOK_EXCLUSION_PREFIX$relativePath"
        if (encoded !in library.scanDirectoryExclusions) {
            repositories.libraryRepository.save(
                library.copy(scanDirectoryExclusions = library.scanDirectoryExclusions + encoded),
            )
        }
        removeIndexedBooks(listOf(book.id))
        cleanupEmptySeries(book.libraryId)
        komgaEvents?.emit(BookDeleted(book.id, book.seriesId, book.libraryId))
    }

    suspend fun restoreExcludedBook(libraryId: KomgaLibraryId, relativePath: String) {
        val library = repositories.libraryRepository.get(libraryId)
        check(library.id.isLocalLibrary()) { "Not a local library" }
        val encoded = "$LOCAL_BOOK_EXCLUSION_PREFIX$relativePath"
        repositories.libraryRepository.save(
            library.copy(scanDirectoryExclusions = library.scanDirectoryExclusions - encoded),
        )
        scan(libraryId)
    }

    suspend fun scanAll() {
        getLibraries().forEach { scan(it.id) }
    }

    suspend fun scanScheduled() {
        getLibraries()
            .filter { it.scanInterval != ScanInterval.DISABLED }
            .forEach { scan(it.id) }
    }

    suspend fun scan(libraryId: KomgaLibraryId) = scanMutex.withLock {
        val library = repositories.libraryRepository.get(libraryId)
        check(library.mediaServerId == LOCAL_SERVER_ID) { "Not a local library" }
        mutableScanState.value = LocalLibraryScanState(scanningLibraryId = libraryId)

        try {
            val excludedFiles = library.scanDirectoryExclusions.mapNotNull {
                it.removePrefixOrNull(LOCAL_BOOK_EXCLUSION_PREFIX)
            }.toSet()
            val files = platform.listSupportedFiles(library.root)
                .filterNot { it.relativePath in excludedFiles }
            val existingBooks = repositories.bookRepository.findAll()
                .filter { it.libraryId == libraryId }
                .associateBy { it.id }
            val scannedBookIds = mutableSetOf<KomgaBookId>()
            var imported = 0

            val seriesGroups = files
                .groupBy { it.relativePath.substringBeforeLast('/', missingDelimiterValue = "") }
                .toList()
                .sortedBy { it.first }
            for ((seriesPath, seriesFiles) in seriesGroups) {
                val seriesId = KomgaSeriesId("$LOCAL_SERIES_ID_PREFIX${stableId("${library.id.value}/$seriesPath")}")
                val seriesName = seriesPath.substringAfterLast('/').ifBlank { library.name }
                // Books reference their parent series, so create the stable parent first.
                // The final save below replaces the placeholder counts and timestamps.
                saveSeries(seriesId, libraryId, seriesName, emptyList())
                val inspectedBooks = seriesFiles.sortedByNaturalName().mapIndexedNotNull { index, localFile ->
                    val bookId = KomgaBookId("$LOCAL_BOOK_ID_PREFIX${stableId("${library.id.value}/${localFile.relativePath}")}")
                    scannedBookIds += bookId
                    val existing = existingBooks[bookId]
                    val modified = Instant.fromEpochMilliseconds(localFile.lastModifiedEpochMillis.coerceAtLeast(0))
                    val number = extractBookNumber(localFile.displayName, index + 1)
                    if (existing != null && existing.sizeBytes == localFile.sizeBytes &&
                        existing.localFileLastModified == modified
                    ) {
                        val metadata = repositories.bookMetadataRepository.find(bookId)
                        var repaired = false
                        val repairedBook = if (existing.number != number) {
                            repositories.bookRepository.save(existing.copy(number = number))
                            repaired = true
                            existing.copy(number = number)
                        } else {
                            existing
                        }
                        if (metadata != null) {
                            val repairedMetadata = metadata.copy(
                                number = if (metadata.numberLock) metadata.number else number.toString(),
                                numberSort = if (metadata.numberSortLock) metadata.numberSort else number.toFloat(),
                            )
                            if (repairedMetadata != metadata) {
                                repositories.bookMetadataRepository.save(repairedMetadata)
                                repaired = true
                            }
                        }
                        if (repaired) imported++
                        return@mapIndexedNotNull repairedBook
                    }

                    runCatching {
                        val inspection = platform.inspect(localFile)
                        val now = Clock.System.now()
                        val book = OfflineBook(
                            id = bookId,
                            seriesId = seriesId,
                            libraryId = libraryId,
                            name = localFile.displayName,
                            number = number,
                            deleted = false,
                            fileHash = stableId("${localFile.relativePath}:${localFile.sizeBytes}:${localFile.lastModifiedEpochMillis}"),
                            oneshot = seriesFiles.size == 1,
                            url = "local://${localFile.relativePath}",
                            size = formatBytes(localFile.sizeBytes),
                            sizeBytes = localFile.sizeBytes,
                            created = modified,
                            lastModified = now,
                            remoteFileLastModified = modified,
                            localFileLastModified = modified,
                            remoteUnavailable = false,
                            fileDownloadPath = localFile.file,
                        )
                        repositories.bookRepository.save(book)
                        repositories.bookMetadataRepository.save(
                            OfflineBookMetadata(
                                bookId = bookId,
                                title = localFile.displayName.substringBeforeLast('.'),
                                summary = "",
                                number = number.toString(),
                                numberSort = number.toFloat(),
                                releaseDate = null,
                                authors = emptyList(),
                                tags = emptyList(),
                                isbn = "",
                                links = emptyList(),
                                titleLock = false,
                                summaryLock = false,
                                numberLock = false,
                                numberSortLock = false,
                                releaseDateLock = false,
                                authorsLock = false,
                                tagsLock = false,
                                isbnLock = false,
                                linksLock = false,
                                created = modified,
                                lastModified = now,
                            )
                        )
                        repositories.mediaRepository.save(
                            OfflineMedia(
                                bookId = bookId,
                                status = KomgaMediaStatus.READY,
                                mediaType = inspection.mediaType,
                                mediaProfile = inspection.mediaProfile,
                                comment = "",
                                epubDivinaCompatible = inspection.epubDivinaCompatible,
                                pageCount = inspection.pages.size,
                                pages = inspection.pages.map { it.copy(bookId = bookId) },
                                extension = inspection.extension,
                            )
                        )
                        inspection.thumbnail?.let { bytes ->
                            repositories.thumbnailBookRepository.save(
                                OfflineThumbnailBook(
                                    id = KomgaThumbnailId("local-thumb-${stableId(bookId.value)}"),
                                    bookId = bookId,
                                    type = OfflineThumbnailBook.Type.GENERATED,
                                    selected = true,
                                    mediaType = inspection.pages.firstOrNull()?.mediaType ?: "image/jpeg",
                                    fileSize = bytes.size.toLong(),
                                    width = inspection.pages.firstOrNull()?.width ?: 0,
                                    height = inspection.pages.firstOrNull()?.height ?: 0,
                                    url = null,
                                    thumbnail = bytes,
                                )
                            )
                        }
                        imported++
                        book
                    }.onFailure { error ->
                        repositories.logJournalRepository.logError(error) {
                            "Local book import failed '${localFile.displayName}'"
                        }
                    }.getOrNull()
                }

                if (inspectedBooks.isNotEmpty()) {
                    saveSeries(seriesId, libraryId, seriesName, inspectedBooks)
                }
            }

            val removedIds = existingBooks.keys - scannedBookIds
            if (removedIds.isNotEmpty()) {
                removeIndexedBooks(removedIds)
            }
            cleanupEmptySeries(libraryId)
            repositories.logJournalRepository.logInfo {
                "Local library scanned '${library.name}': $imported updated, ${removedIds.size} removed"
            }
            mutableScanState.value = LocalLibraryScanState(
                importedBooks = imported,
                removedBooks = removedIds.size,
            )
        } catch (error: Throwable) {
            repositories.logJournalRepository.logError(error) { "Local library scan failed '${library.name}'" }
            mutableScanState.value = LocalLibraryScanState(error = error.message ?: error::class.simpleName)
            throw error
        }
    }

    private suspend fun removeIndexedBooks(bookIds: Collection<KomgaBookId>) {
        if (bookIds.isEmpty()) return
        repositories.thumbnailBookRepository.deleteByBookIds(bookIds)
        repositories.mediaRepository.delete(bookIds.toList())
        repositories.bookMetadataRepository.delete(bookIds.toList())
        repositories.bookRepository.delete(bookIds)
    }

    fun startScheduledScanning() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            runCatching { getLibraries().filter { it.scanOnStartup }.forEach { scan(it.id) } }
            if (platform.scheduledScanningIsManagedByPlatform) return@launch
            while (isActive) {
                delay(1.hours)
                getLibraries().filter { it.scanInterval != ScanInterval.DISABLED }.forEach { library ->
                    runCatching { scan(library.id) }
                }
            }
        }
    }

    private suspend fun ensureLocalServer() {
        if (repositories.mediaServerRepository.find(LOCAL_SERVER_ID) == null) {
            repositories.mediaServerRepository.save(OfflineMediaServer(LOCAL_SERVER_ID, LOCAL_SERVER_URL))
        }
    }

    private suspend fun saveSeries(
        seriesId: KomgaSeriesId,
        libraryId: KomgaLibraryId,
        name: String,
        books: List<OfflineBook>,
    ) {
        val now = Clock.System.now()
        val created = books.minOfOrNull { it.created } ?: now
        repositories.seriesRepository.save(
            OfflineSeries(
                id = seriesId,
                libraryId = libraryId,
                name = name,
                url = "local://series/${seriesId.value}",
                oneshot = books.size == 1,
                bookCount = books.size,
                deleted = false,
                created = created,
                lastModified = now,
                fileLastModified = books.maxOfOrNull { it.localFileLastModified } ?: now,
            )
        )
        repositories.seriesMetadataRepository.save(
            OfflineSeriesMetadata(
                seriesId = seriesId,
                status = KomgaSeriesStatus.ONGOING,
                statusLock = false,
                title = name,
                alternateTitles = emptyList(),
                alternateTitlesLock = false,
                titleLock = false,
                titleSort = name,
                titleSortLock = false,
                summary = "",
                summaryLock = false,
                readingDirection = null,
                readingDirectionLock = false,
                publisher = "",
                publisherLock = false,
                ageRating = null,
                ageRatingLock = false,
                language = "",
                languageLock = false,
                genres = emptyList(),
                genresLock = false,
                tags = emptyList(),
                tagsLock = false,
                totalBookCount = books.size,
                totalBookCountLock = false,
                sharingLabels = emptyList(),
                sharingLabelsLock = false,
                links = emptyList(),
                linksLock = false,
            )
        )
        repositories.bookMetadataAggregationRepository.save(
            OfflineBookMetadataAggregation(seriesId = seriesId, createdDate = created, lastModifiedDate = now)
        )
    }

    private suspend fun cleanupEmptySeries(libraryId: KomgaLibraryId) {
        repositories.seriesRepository.findAllByLibraryId(libraryId).forEach { series ->
            if (repositories.bookRepository.findAllIdsBySeriesId(series.id).isEmpty()) {
                repositories.bookMetadataAggregationRepository.delete(series.id)
                repositories.seriesMetadataRepository.delete(series.id)
                repositories.seriesRepository.delete(series.id)
            }
        }
    }
}

internal fun OfflineLibrary.isLocalSourceLibrary(): Boolean =
    mediaServerId == LOCAL_SERVER_ID || id.isLocalLibrary()

internal fun stableId(value: String): String {
    var hash = -0x340d631b7bdddcdbL
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}

private fun List<LocalLibraryFile>.sortedByNaturalName(): List<LocalLibraryFile> =
    sortedWith(compareBy({ naturalSortKey(it.displayName) }, { it.relativePath }))

private fun naturalSortKey(value: String): String = buildString {
    Regex("\\d+|\\D+").findAll(value.lowercase()).forEach { part ->
        val token = part.value
        if (token.firstOrNull()?.isDigit() == true) append(token.padStart(16, '0')) else append(token)
    }
}

private val labeledBookNumber = Regex(
    pattern = "(?i)(?:chapter|chap|ch|episode|ep|issue|book|volume|vol|v|#)\\s*[._ -]*([0-9]+)",
)

private const val LOCAL_BOOK_EXCLUSION_PREFIX = "local-file:"

data class LocalBookExclusion(
    val libraryId: KomgaLibraryId,
    val libraryName: String,
    val relativePath: String,
    val displayName: String,
)

private fun String.removePrefixOrNull(prefix: String): String? =
    if (startsWith(prefix)) removePrefix(prefix) else null

private fun extractBookNumber(name: String, fallback: Int): Int {
    val stem = name.substringBeforeLast('.')
    val labeledNumber = labeledBookNumber.findAll(stem)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (labeledNumber != null) return labeledNumber

    return Regex("\\d+").findAll(stem).lastOrNull()?.value?.toIntOrNull() ?: fallback
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KiB"
    else -> "${bytes / (1024 * 1024)} MiB"
}
