package snd.komelia.db.offline

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import snd.komelia.db.ExposedTransactionTemplate
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.OfflineSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.offline.dto.ExposedOfflineBookDtoRepository
import snd.komelia.db.offline.dto.ExposedOfflineReferentialRepository
import snd.komelia.db.offline.dto.ExposedSeriesDtoRepository
import snd.komelia.db.repository.OfflineSettingsRepositoryWrapper
import snd.komelia.offline.OfflineRepositories
import snd.komelia.offline.local.LocalBookInspection
import snd.komelia.offline.local.LocalLibraryFile
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.offline.local.LocalLibraryPlatform
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.book.KomgaBookId
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.book.MediaProfile
import snd.komga.client.library.ScanInterval
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalLibraryManagerIntegrationTest {
    @Test
    fun paginatesLargeLocalLibraryWithAccurateTotals() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-local-pagination-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = (1..123).map { index ->
                file(
                    relativePath = "Large Series/Book ${index.toString().padStart(3, '0')}.cbz",
                    size = 100L + index,
                    modified = 1_000L + index,
                )
            }
        }
        val manager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Large local library")

        val firstPage = manager.getBooks(
            KomgaPageRequest(pageIndex = 0, size = 50),
        )
        val lastPage = manager.getBooks(
            KomgaPageRequest(pageIndex = 2, size = 50),
        )

        assertEquals(123, firstPage.totalElements)
        assertEquals(3, firstPage.totalPages)
        assertEquals(50, firstPage.content.size)
        assertEquals(23, lastPage.content.size)
    }

    @Test
    fun importsIncrementallyPersistsAndHonorsDisabledSchedule() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-local-library-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory)
        val manager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        manager.prepareLocalMode()
        assertEquals("root", repositories.userRepository.get(snd.komelia.offline.user.model.OfflineUser.ROOT).email)

        platform.files = listOf(platform.file("Series A/Book 01.cbz", size = 100, modified = 1_000))
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")

        assertEquals(listOf("Book 01.cbz"), repositories.bookRepository.findAll().map { it.name })
        assertEquals(1, platform.inspectionCount)
        assertEquals(1, repositories.seriesRepository.findAllByLibraryId(library.id).single().bookCount)

        // A new manager instance models an application restart against the same persistent database.
        val restarted = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val restartedBook = restarted.getBooks().content.single()
        assertEquals("Book 01.cbz", restartedBook.name)
        assertEquals("application/zip", restartedBook.media.mediaType)
        assertEquals(1, restartedBook.media.pagesCount)

        restarted.scanAll()
        assertEquals(1, platform.inspectionCount, "unchanged files must not be reparsed")

        platform.files = platform.files + platform.file("Series A/Book 02.cbz", size = 200, modified = 2_000)
        restarted.scanAll()
        assertEquals(listOf("Book 01.cbz", "Book 02.cbz"), repositories.bookRepository.findAll().map { it.name }.sorted())
        assertEquals(2, platform.inspectionCount)

        platform.files = platform.files.filterNot { it.displayName == "Book 01.cbz" }
        restarted.scanAll()
        assertEquals(listOf("Book 02.cbz"), repositories.bookRepository.findAll().map { it.name })

        restarted.updateScanInterval(library.id, ScanInterval.DISABLED)
        platform.files = platform.files + platform.file("Series A/Book 03.cbz", size = 300, modified = 3_000)
        restarted.scanScheduled()
        assertEquals(listOf("Book 02.cbz"), repositories.bookRepository.findAll().map { it.name })

        restarted.scanAll()
        assertEquals(listOf("Book 02.cbz", "Book 03.cbz"), repositories.bookRepository.findAll().map { it.name }.sorted())
    }

    private fun createRepositories(database: KomeliaDatabase, tempDirectory: Path): OfflineRepositories {
        val storedSettings = ExposedOfflineSettingsRepository(database.offline)
        val settings = OfflineSettingsRepositoryWrapper(
            SettingsStateWrapper(
                settings = OfflineSettings(downloadDirectory = PlatformFile(tempDirectory.resolve("downloads").toFile())),
                saveSettings = storedSettings::save,
            )
        )
        return OfflineRepositories(
            mediaServerRepository = ExposedOfflineMediaServerRepository(database.offline),
            mediaRepository = ExposedMediaRepository(database.offline),
            bookRepository = ExposedOfflineBookRepository(database.offline),
            bookMetadataRepository = ExposedOfflineBookMetadataRepository(database.offline),
            bookMetadataAggregationRepository = ExposedOfflineBookMetadataAggregationRepository(database.offline),
            libraryRepository = ExposedOfflineLibraryRepository(database.offline),
            readProgressRepository = ExposedOfflineReadProgressRepository(database.offline),
            seriesMetadataRepository = ExposedOfflineSeriesMetadataRepository(database.offline),
            seriesRepository = ExposedOfflineSeriesRepository(database.offline),
            thumbnailBookRepository = ExposedOfflineThumbnailBookRepository(database.offline),
            thumbnailSeriesRepository = ExposedOfflineThumbnailSeriesRepository(database.offline),
            userRepository = ExposedOfflineUserRepository(database.offline),
            bookDtoRepository = ExposedOfflineBookDtoRepository(database.offline),
            referentialRepository = ExposedOfflineReferentialRepository(database.offline),
            seriesDtoRepository = ExposedSeriesDtoRepository(database.offline),
            logJournalRepository = ExposedLogJournalRepository(database.offline),
            transactionTemplate = ExposedTransactionTemplate(database.offline),
            tasksRepository = ExposedOfflineTasksRepository(database.offline),
            offlineSettingsRepository = settings,
        )
    }

    private class FakeLocalLibraryPlatform(private val sourceDirectory: Path) : LocalLibraryPlatform {
        var files: List<LocalLibraryFile> = emptyList()
        var inspectionCount: Int = 0

        fun file(relativePath: String, size: Long, modified: Long) = LocalLibraryFile(
            file = PlatformFile(sourceDirectory.resolve(relativePath).toFile()),
            relativePath = relativePath,
            displayName = relativePath.substringAfterLast('/'),
            sizeBytes = size,
            lastModifiedEpochMillis = modified,
        )

        override suspend fun listSupportedFiles(root: String): List<LocalLibraryFile> = files

        override suspend fun inspect(file: LocalLibraryFile): LocalBookInspection {
            inspectionCount++
            return LocalBookInspection(
                mediaType = "application/zip",
                mediaProfile = MediaProfile.DIVINA,
                pages = listOf(
                    OfflineBookPage(
                        bookId = KomgaBookId(""),
                        fileName = "001.jpg",
                        mediaType = "image/jpeg",
                        width = 100,
                        height = 200,
                        fileSize = 10,
                    )
                ),
            )
        }
    }
}
