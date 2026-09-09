package snd.komelia.db.offline

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
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
import snd.komelia.offline.local.AvailableBookSource
import snd.komelia.offline.local.AvailableBooksRepository
import snd.komelia.offline.local.LocalLibraryFile
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.offline.local.LocalLibraryPlatform
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komelia.offline.server.model.OfflineMediaServer
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komelia.offline.series.model.OfflineBookMetadataAggregation
import snd.komelia.offline.readprogress.OfflineReadProgress
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.book.KomgaBookId
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaSort.KomgaBooksSort
import snd.komga.client.book.MediaProfile
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.library.ScanInterval
import snd.komga.client.series.KomgaSeriesId
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import snd.komelia.offline.local.createLocalLibraryPlatform
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLibraryManagerIntegrationTest {
    @Test
    fun cancelledReinspectionPreservesTheExistingSeriesAndBooks() = runBlocking {
        val root = createTempDirectory("komelia-cancelled-scan-test")
        val source = root.resolve("source").createDirectories()
        val repositories = createRepositories(KomeliaDatabase(root.toString()), root)
        val delegate = FakeLocalLibraryPlatform(source).apply {
            files = listOf(file("Series/Chapter 1.cbz", 100, 1_000))
        }
        var cancel = false
        val platform = object : LocalLibraryPlatform by delegate {
            override suspend fun inspect(file: LocalLibraryFile): LocalBookInspection {
                if (cancel) throw kotlinx.coroutines.CancellationException("synthetic cancelled scan")
                return delegate.inspect(file)
            }
        }
        val manager = LocalLibraryManager(repositories, platform, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val library = manager.addLibrary(PlatformFile(source.toFile()), "Synthetic books")
        val originalSeries = repositories.seriesRepository.findAllByLibraryId(library.id).single()
        val originalBook = repositories.bookRepository.findAll().single()
        delegate.files = listOf(delegate.file("Series/Chapter 1.cbz", 101, 2_000))
        cancel = true
        assertFailsWith<kotlinx.coroutines.CancellationException> { manager.scan(library.id) }
        assertEquals(originalSeries, repositories.seriesRepository.get(originalSeries.id))
        assertEquals(originalBook, repositories.bookRepository.get(originalBook.id))
    }

    @Test
    fun localImportAndRescanKeepDecimalChapterNumbers() = runBlocking {
        val root = createTempDirectory("komelia-decimal-import-test")
        val source = root.resolve("source").createDirectories()
        val repositories = createRepositories(KomeliaDatabase(root.toString()), root)
        val platform = FakeLocalLibraryPlatform(source).apply {
            files = listOf("Chapter 1.cbz", "Chapter 1.5.cbz", "Chapter 2.cbz").map {
                file("Series/$it", 100, 1_000)
            }
        }
        val manager = LocalLibraryManager(repositories, platform, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val library = manager.addLibrary(PlatformFile(source.toFile()), "Synthetic chapters")
        repeat(2) {
            val books = manager.getBooks(KomgaPageRequest(unpaged = true, sort = KomgaBooksSort.byNumberAsc())).content
            assertEquals(listOf(1f, 1.5f, 2f), books.map { it.metadata.numberSort })
            assertEquals(listOf("1", "1.5", "2"), books.map { it.metadata.number })
            assertEquals(books[1].id, repositories.bookDtoRepository.findNextInSeriesOrNull(books[0].id, OfflineUser.ROOT)?.id)
            assertEquals(books[2].id, repositories.bookDtoRepository.findNextInSeriesOrNull(books[1].id, OfflineUser.ROOT)?.id)
            manager.scan(library.id)
        }
        assertEquals(3, platform.inspectionCount)
    }

    @Test
    fun siblingNavigationPreservesDecimalsAndBothSeriesBoundaries() = runBlocking {
        val root = createTempDirectory("komelia-sibling-test")
        val source = root.resolve("source").createDirectories()
        val repositories = createRepositories(KomeliaDatabase(root.toString()), root)
        val numbers = listOf(1f, 1.5f, 2f, 3f, 4f)
        val platform = FakeLocalLibraryPlatform(source).apply {
            files = numbers.indices.map { file("Series/chapter-$it.cbz", 100, 1_000) }
        }
        val manager = LocalLibraryManager(repositories, platform, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        manager.addLibrary(PlatformFile(source.toFile()), "Synthetic chapters")
        val books = repositories.bookRepository.findAll().sortedBy { it.name }
        books.zip(numbers).forEach { (book, number) ->
            val metadata = repositories.bookMetadataRepository.get(book.id)
            repositories.bookMetadataRepository.save(metadata.copy(number = number.toString(), numberSort = number))
        }
        books.forEachIndexed { index, book ->
            assertEquals(books.getOrNull(index + 1)?.id,
                repositories.bookDtoRepository.findNextInSeriesOrNull(book.id, OfflineUser.ROOT)?.id,
                "next chapter at index $index")
            assertEquals(books.getOrNull(index - 1)?.id,
                repositories.bookDtoRepository.findPreviousInSeriesOrNull(book.id, OfflineUser.ROOT)?.id,
                "previous chapter at index $index")
        }
    }

    @Test
    fun reindexesLegacyEpubOnceWithoutChangingIdentityMetadataOrProgress() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-epub-reindex-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val repositories = createRepositories(KomeliaDatabase(tempDirectory.toString()), tempDirectory)
        val currentExtension = snd.komelia.offline.media.model.MediaExtensionEpub(
            manifest = snd.komga.client.book.WPPublication(links = emptyList(), metadata = snd.komga.client.book.WPMetadata(title = "Novel")),
            localInspectionVersion = 1,
            positions = listOf(0f, 1f).map { progress ->
                snd.komga.client.book.R2Locator("chapter.xhtml", "application/xhtml+xml",
                    locations = snd.komga.client.book.R2Location(progression = progress, totalProgression = progress))
            },
        )
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(file("Novel.epub", size = 100, modified = 1_000))
            inspection = LocalBookInspection("application/epub+zip", MediaProfile.EPUB, emptyList(), currentExtension)
        }
        val manager = LocalLibraryManager(repositories, platform, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val book = repositories.bookRepository.findAll().single()
        val media = repositories.mediaRepository.get(book.id)
        repositories.mediaRepository.save(media.copy(extension = currentExtension.copy(localInspectionVersion = 0, positions = emptyList())))
        val metadata = repositories.bookMetadataRepository.get(book.id).copy(
            title = "My title", titleLock = true, number = "Special", numberLock = true,
        )
        repositories.bookMetadataRepository.save(metadata)
        val progress = OfflineReadProgress(book.id, OfflineUser.ROOT, page = 0, completed = false,
            locator = snd.komga.client.book.R2Locator("chapter.xhtml", "application/xhtml+xml"))
        repositories.readProgressRepository.save(progress)
        val storedProgress = repositories.readProgressRepository.find(book.id, OfflineUser.ROOT)

        manager.scan(library.id)

        assertEquals(2, platform.inspectionCount)
        assertEquals(book.id, repositories.bookRepository.findAll().single().id)
        assertEquals(metadata, repositories.bookMetadataRepository.get(book.id))
        assertEquals(storedProgress, repositories.readProgressRepository.find(book.id, OfflineUser.ROOT))
        assertEquals(currentExtension, repositories.mediaRepository.get(book.id).extension)
        assertEquals(0, repositories.mediaRepository.get(book.id).pageCount)
        manager.scan(library.id)
        assertEquals(2, platform.inspectionCount, "the migrated unchanged EPUB must not be reopened")

        // The first browser callback after migration must still save through the real transaction/repository path.
        val action = snd.komelia.offline.readprogress.actions.ProgressMarkProgressionAction(
            repositories.mediaRepository, repositories.readProgressRepository,
            repositories.transactionTemplate, kotlinx.coroutines.flow.MutableSharedFlow(),
        )
        action.run(book.id, OfflineUser.ROOT, snd.komga.client.book.R2Progression(
            modified = kotlin.time.Instant.fromEpochMilliseconds(1_000),
            device = snd.komga.client.book.R2Device("test", "Test"),
            locator = snd.komga.client.book.R2Locator("http://komelia/api/v1/books/${book.id.value}/resource/chapter.xhtml", "application/xhtml+xml",
                locations = snd.komga.client.book.R2Location(progression = 0.5f)),
        ))
        assertEquals(0.5f, repositories.readProgressRepository.find(book.id, OfflineUser.ROOT)?.locator?.locations?.totalProgression)
    }

    @Test
    fun separatesLocalSourceBooksFromRemoteDownloads() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-local-source-isolation-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(file("Local Series/Local Book.epub", size = 101, modified = 101_000))
        }
        val manager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val localLibrary = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val localSeries = repositories.seriesRepository.findAllByLibraryId(localLibrary.id).single()
        val localBook = repositories.bookRepository.findAll().single()

        val remoteServerId = OfflineMediaServerId("remote-server")
        val remoteLibraryId = KomgaLibraryId("remote-library")
        val remoteSeriesId = KomgaSeriesId("remote-series")
        val remoteBookId = KomgaBookId("remote-book")
        repositories.mediaServerRepository.save(OfflineMediaServer(id = remoteServerId, url = "https://example.invalid"))
        repositories.libraryRepository.save(
            localLibrary.copy(id = remoteLibraryId, mediaServerId = remoteServerId, name = "Remote"),
        )
        repositories.seriesRepository.save(
            localSeries.copy(id = remoteSeriesId, libraryId = remoteLibraryId, name = "Remote Series"),
        )
        repositories.seriesMetadataRepository.save(
            checkNotNull(repositories.seriesMetadataRepository.find(localSeries.id)).copy(
                seriesId = remoteSeriesId,
                title = "Remote Series",
            ),
        )
        repositories.bookMetadataAggregationRepository.save(
            OfflineBookMetadataAggregation(seriesId = remoteSeriesId),
        )
        repositories.bookRepository.save(
            localBook.copy(
                id = remoteBookId,
                seriesId = remoteSeriesId,
                libraryId = remoteLibraryId,
                name = "Remote Download.epub",
            ),
        )
        repositories.bookMetadataRepository.save(
            repositories.bookMetadataRepository.get(localBook.id).copy(
                bookId = remoteBookId,
                title = "Remote Download",
            ),
        )
        repositories.mediaRepository.save(
            repositories.mediaRepository.get(localBook.id).copy(bookId = remoteBookId),
        )

        assertEquals(listOf("Local Book.epub"), manager.getBooks().content.map { it.name })
        assertEquals(listOf("Remote Download.epub"), manager.getRemoteDownloadedBooks().content.map { it.name })
        val availableBooks = AvailableBooksRepository(repositories)
        assertEquals(
            listOf("Local Book.epub", "Remote Download.epub"),
            availableBooks.getBooks(AvailableBookSource.ALL).content.map { it.name }.sorted(),
        )
        assertEquals(
            listOf("Remote Download.epub"),
            availableBooks.getBooks(AvailableBookSource.DOWNLOADED, query = "Download").content.map { it.name },
        )
        assertEquals(
            emptyList(),
            availableBooks.getBooks(AvailableBookSource.LOCAL, query = "Download").content.map { it.name },
        )
        assertEquals(
            listOf("Local Book.epub"),
            availableBooks.getBooks(AvailableBookSource.LOCAL, query = "Book").content.map { it.name },
        )
    }

    @Test
    fun excludesOneLocalBookWithoutDeletingTheSourceAndRestoresItAfterRestart() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-local-exclusion-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(
                file("Series/Chapter 01.cbz", size = 101, modified = 101_000),
                file("Series/Chapter 02.cbz", size = 102, modified = 102_000),
            )
        }
        val manager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val excludedBook = manager.getBooks(KomgaPageRequest(unpaged = true)).content
            .first { it.name == "Chapter 01.cbz" }

        repositories.readProgressRepository.save(
            OfflineReadProgress(excludedBook.id, OfflineUser.ROOT, page = 1, completed = true),
        )
        manager.excludeBook(excludedBook.id)
        assertNull(repositories.readProgressRepository.find(excludedBook.id, OfflineUser.ROOT))
        assertEquals(listOf("Chapter 02.cbz"), manager.getBooks(KomgaPageRequest(unpaged = true)).content.map { it.name })
        assertEquals(listOf("Series/Chapter 01.cbz"), manager.getExcludedBooks().map { it.relativePath })
        assertTrue(platform.files.any { it.relativePath == "Series/Chapter 01.cbz" }, "source file must remain untouched")

        val restartedManager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        restartedManager.scanAll()
        assertEquals(listOf("Chapter 02.cbz"), restartedManager.getBooks(KomgaPageRequest(unpaged = true)).content.map { it.name })

        restartedManager.restoreExcludedBook(library.id, "Series/Chapter 01.cbz")
        assertEquals(
            listOf("Chapter 01.cbz", "Chapter 02.cbz"),
            restartedManager.getBooks(
                KomgaPageRequest(unpaged = true, sort = KomgaBooksSort.byNumberAsc()),
            ).content.map { it.name },
        )
        assertTrue(restartedManager.getExcludedBooks().isEmpty())
    }

    @Test
    fun sortsLocalChaptersByThePrimaryChapterNumberInsteadOfTrailingReleaseHash() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-local-number-sort-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(
                file("Series/Chapter 105_020862.cbz", size = 105, modified = 105_000),
                file("Series/Chapter 106_50ae17.cbz", size = 106, modified = 106_000),
                file("Series/Chapter 107_44b33f.cbz", size = 107, modified = 107_000),
                file("Series/Chapter 108_536edb.cbz", size = 108, modified = 108_000),
                file("Series/Chapter 109_2015a8.cbz", size = 109, modified = 109_000),
            )
        }
        val manager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

        manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")

        val legacyNumbers = listOf(20_862f, 17f, 33f, 536f, 8f)
        repositories.bookRepository.findAll()
            .sortedBy { it.name }
            .zip(legacyNumbers)
            .forEach { (book, legacyNumber) ->
                val metadata = repositories.bookMetadataRepository.get(book.id)
                repositories.bookMetadataRepository.save(
                    metadata.copy(number = legacyNumber.toInt().toString(), numberSort = legacyNumber),
                )
            }

        manager.scanAll()
        assertEquals(5, platform.inspectionCount, "metadata repair must not re-open unchanged archives")

        val books = manager.getBooks(
            KomgaPageRequest(unpaged = true, sort = KomgaBooksSort.byNumberAsc()),
        ).content

        assertEquals(
            listOf(
                "Chapter 105_020862.cbz",
                "Chapter 106_50ae17.cbz",
                "Chapter 107_44b33f.cbz",
                "Chapter 108_536edb.cbz",
                "Chapter 109_2015a8.cbz",
            ),
            books.map { it.name },
        )
    }

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

    @Test
    fun refreshRemovesExternallyDeletedReadBookAndPreservesSurvivingProgress() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-external-delete-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(
                file("Series/Chapter 01.cbz", size = 101, modified = 101_000),
                file("Series/Chapter 02.cbz", size = 102, modified = 102_000),
            )
        }
        val manager = LocalLibraryManager(
            repositories = repositories,
            platform = platform,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        manager.prepareLocalMode()
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val books = repositories.bookRepository.findAll().sortedBy { it.name }
        books.forEach { book ->
            repositories.readProgressRepository.save(
                OfflineReadProgress(book.id, OfflineUser.ROOT, page = 1, completed = true),
            )
        }
        val survivingProgress = repositories.readProgressRepository.find(books.last().id, OfflineUser.ROOT)

        // A file manager removes a book which already has persisted reading progress.
        platform.files = platform.files.drop(1)
        manager.scan(library.id)

        assertEquals(listOf(books.last().id), repositories.bookRepository.findAll().map { it.id })
        assertNull(repositories.readProgressRepository.find(books.first().id, OfflineUser.ROOT))
        assertEquals(survivingProgress, repositories.readProgressRepository.find(books.last().id, OfflineUser.ROOT))
        assertEquals(1, repositories.seriesRepository.findAllByLibraryId(library.id).single().bookCount)

        // Removing the containing folder must also remove its now-empty series.
        platform.files = emptyList()
        manager.scan(library.id)
        assertTrue(repositories.bookRepository.findAll().isEmpty())
        assertTrue(repositories.seriesRepository.findAllByLibraryId(library.id).isEmpty())
        assertNull(repositories.readProgressRepository.find(books.last().id, OfflineUser.ROOT))
        manager.scanAll()
    }

    @Test
    fun removingLocalLibraryCleansProgressWithoutTouchingSourceFiles() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-remove-local-library-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val repositories = createRepositories(KomeliaDatabase(tempDirectory.toString()), tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(file("Series/Read book.cbz", size = 100, modified = 1_000))
        }
        val manager = LocalLibraryManager(repositories, platform, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val book = repositories.bookRepository.findAll().single()
        repositories.readProgressRepository.save(
            OfflineReadProgress(book.id, OfflineUser.ROOT, page = 1, completed = true),
        )

        manager.removeLibrary(library.id)

        assertTrue(manager.getLibraries().isEmpty())
        assertTrue(repositories.bookRepository.findAll().isEmpty())
        assertTrue(repositories.seriesRepository.findAllByLibraryId(library.id).isEmpty())
        assertNull(repositories.readProgressRepository.find(book.id, OfflineUser.ROOT))
        assertEquals(1, platform.files.size, "removing an index must not delete source files")
    }

    @Test
    fun failedBookRemovalRollsBackProgressAndMetadataCleanup() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-local-removal-rollback-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val database = KomeliaDatabase(tempDirectory.toString())
        val repositories = createRepositories(database, tempDirectory)
        val platform = FakeLocalLibraryPlatform(sourceDirectory).apply {
            files = listOf(file("Series/Read book.cbz", size = 100, modified = 1_000))
        }
        val manager = LocalLibraryManager(repositories, platform, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val book = repositories.bookRepository.findAll().single()
        repositories.readProgressRepository.save(
            OfflineReadProgress(book.id, OfflineUser.ROOT, page = 1, completed = true),
        )
        val progress = repositories.readProgressRepository.find(book.id, OfflineUser.ROOT)
        val media = repositories.mediaRepository.get(book.id)
        val metadata = repositories.bookMetadataRepository.get(book.id)
        suspendTransaction(db = database.offline) {
            exec("CREATE TRIGGER reject_book_delete BEFORE DELETE ON BOOK BEGIN SELECT RAISE(ABORT, 'injected delete failure'); END")
        }
        platform.files = emptyList()

        assertFailsWith<ExposedSQLException> { manager.scan(library.id) }

        assertEquals(book, repositories.bookRepository.get(book.id))
        assertEquals(progress, repositories.readProgressRepository.find(book.id, OfflineUser.ROOT))
        assertEquals(media, repositories.mediaRepository.get(book.id))
        assertEquals(metadata, repositories.bookMetadataRepository.get(book.id))
    }

    @Test
    fun desktopRefreshHandlesActualExternalFileAndFolderDeletion() = runBlocking {
        val tempDirectory = createTempDirectory("komelia-desktop-external-delete-test")
        val sourceDirectory = tempDirectory.resolve("source").createDirectories()
        val seriesDirectory = sourceDirectory.resolve("Series").createDirectories()
        val archive = seriesDirectory.resolve("Read book.cbz").toFile()
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("001.png"))
            zip.write(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jR5sAAAAASUVORK5CYII="))
            zip.closeEntry()
        }
        val repositories = createRepositories(KomeliaDatabase(tempDirectory.toString()), tempDirectory)
        val manager = LocalLibraryManager(
            repositories, checkNotNull(createLocalLibraryPlatform()), CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
        val library = manager.addLibrary(PlatformFile(sourceDirectory.toFile()), "Local")
        val book = repositories.bookRepository.findAll().single()
        repositories.readProgressRepository.save(OfflineReadProgress(book.id, OfflineUser.ROOT, page = 1, completed = true))

        assertTrue(archive.delete())
        assertTrue(seriesDirectory.toFile().delete())
        manager.scan(library.id)

        assertTrue(repositories.bookRepository.findAll().isEmpty())
        assertTrue(repositories.seriesRepository.findAllByLibraryId(library.id).isEmpty())
        assertNull(repositories.readProgressRepository.find(book.id, OfflineUser.ROOT))
        manager.scanAll()
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
        var inspection: LocalBookInspection? = null

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
            inspection?.let { return it }
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
