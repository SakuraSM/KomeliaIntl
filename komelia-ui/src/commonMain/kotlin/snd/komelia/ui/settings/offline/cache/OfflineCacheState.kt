package snd.komelia.ui.settings.offline.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import snd.komelia.AppNotification
import snd.komelia.AppNotificationMessageKey
import snd.komelia.AppNotifications
import snd.komelia.offline.book.actions.BookDeleteAction
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.offline.media.repository.OfflineMediaRepository
import snd.komelia.offline.series.actions.SeriesDeleteAction
import snd.komelia.offline.series.repository.OfflineSeriesRepository
import snd.komelia.ui.LoadState
import snd.komga.client.book.MediaProfile

internal class OfflineCacheState(
    private val bookRepository: OfflineBookRepository,
    private val seriesRepository: OfflineSeriesRepository,
    private val mediaRepository: OfflineMediaRepository,
    private val bookDeleteAction: BookDeleteAction,
    private val seriesDeleteAction: SeriesDeleteAction,
    private val notifications: AppNotifications,
    private val coroutineScope: CoroutineScope,
) {
    private val _catalog = MutableStateFlow(OfflineCacheCatalog(emptyList(), emptyList()))
    val catalog = _catalog.asStateFlow()
    private val _loadState = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val loadState = _loadState.asStateFlow()
    val selectedMediaKind = MutableStateFlow<OfflineCacheMediaKind?>(null)

    suspend fun initialize() {
        if (_loadState.value == LoadState.Uninitialized) load()
    }

    fun selectMediaKind(kind: OfflineCacheMediaKind?) {
        selectedMediaKind.value = kind
    }

    fun deleteBook(bookId: String) = runMutation {
        val book = bookRepository.get(snd.komga.client.book.KomgaBookId(bookId))
        check(!book.libraryId.isLocalLibrary()) { "Local source files are not cache entries" }
        bookDeleteAction.execute(book.id)
        if (bookRepository.findAll(book.seriesId).isEmpty() && seriesRepository.find(book.seriesId) != null) {
            seriesDeleteAction.execute(book.seriesId)
        }
    }

    fun deleteSeries(seriesId: String) = runMutation {
        val series = seriesRepository.get(snd.komga.client.series.KomgaSeriesId(seriesId))
        check(!series.libraryId.isLocalLibrary()) { "Local source files are not cache entries" }
        seriesDeleteAction.execute(series.id)
    }

    fun deleteAll() = runMutation {
        seriesRepository.findAll()
            .filterNot { it.libraryId.isLocalLibrary() }
            .forEach { seriesDeleteAction.execute(it.id) }
        bookRepository.findAll()
            .filterNot { it.libraryId.isLocalLibrary() }
            .forEach { bookDeleteAction.execute(it.id) }
    }

    fun retry() {
        coroutineScope.launch { load() }
    }

    private fun runMutation(block: suspend () -> Unit) {
        if (_loadState.value == LoadState.Loading) return
        _loadState.value = LoadState.Loading
        notifications.runCatchingToNotifications(
            coroutineScope = coroutineScope,
            onFailure = { _loadState.value = LoadState.Error(it) },
            onSuccess = {
                notifications.add(AppNotification.Success(AppNotificationMessageKey.OFFLINE_CACHE_CLEARED))
                coroutineScope.launch { load() }
            },
            block = block,
        )
    }

    private suspend fun load() {
        _loadState.value = LoadState.Loading
        notifications.runCatchingToNotifications {
            val series = seriesRepository.findAll()
            val books = bookRepository.findAll()
            val mediaByBook = mediaRepository.findAll(books.map { it.id }).associateBy { it.bookId }
            _catalog.value = buildOfflineCacheCatalog(
                series = series.map { OfflineCacheSeriesRecord(it.id.value, it.name, it.libraryId.value) },
                books = books.map { book ->
                    val profile = mediaByBook[book.id]?.mediaProfile
                    OfflineCacheBookRecord(
                        id = book.id.value,
                        seriesId = book.seriesId.value,
                        title = book.name,
                        mediaKind = when (profile) {
                            MediaProfile.DIVINA -> OfflineCacheMediaKind.COMIC
                            MediaProfile.EPUB -> OfflineCacheMediaKind.EPUB
                            MediaProfile.PDF -> OfflineCacheMediaKind.PDF
                            else -> OfflineCacheMediaKind.OTHER
                        },
                        sizeBytes = book.sizeBytes,
                        updatedEpochSeconds = book.localFileLastModified.epochSeconds,
                        isAvailable = isOfflineCacheFileAvailable(book.fileDownloadPath),
                        libraryId = book.libraryId.value,
                    )
                },
            )
        }.fold(
            onSuccess = { _loadState.value = LoadState.Success(Unit) },
            onFailure = { _loadState.value = LoadState.Error(it) },
        )
    }
}
