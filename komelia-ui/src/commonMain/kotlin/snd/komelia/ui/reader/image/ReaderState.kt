package snd.komelia.ui.reader.image

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.AppNotification
import snd.komelia.AppNotificationMessageKey
import snd.komelia.AppNotifications
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.ReduceKernel
import snd.komelia.image.UpsamplingMode
import snd.komelia.image.availableReduceKernels
import snd.komelia.image.availableUpsamplingModes
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.KomgaSeriesApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.ReaderFlashColor
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.MainScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.CommonParcelable
import snd.komelia.ui.platform.CommonParcelize
import snd.komelia.ui.platform.CommonParcelizeRawValue
import snd.komelia.ui.series.SeriesScreen
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.series.KomgaSeries

typealias SpreadIndex = Int

class ReaderState(
    private val initialBook: KomeliaBook?,
    private val bookApi: KomgaBookApi,
    private val seriesApi: KomgaSeriesApi,
    private val readListApi: KomgaReadListApi,
    private val navigator: Navigator,
    private val appNotifications: AppNotifications,
    private val readerSettingsRepository: ImageReaderSettingsRepository,
    private val currentBookId: MutableStateFlow<KomgaBookId?>,
    private val markReadProgress: Boolean,
    private val stateScope: CoroutineScope,
    private val bookSiblingsContext: BookSiblingsContext,
    private val colorCorrectionRepository: BookColorCorrectionRepository,
    val pageChangeFlow: SharedFlow<Unit>,
) {
    private val previewLoadScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    val state = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val expandImageSettings = MutableStateFlow(false)

    val booksState = MutableStateFlow<BookState?>(null)
    val retryingSibling = MutableStateFlow<Boolean?>(null)
    private val navigationMutex = Mutex()
    val series = MutableStateFlow<KomgaSeries?>(null)

    val readerType = MutableStateFlow(ReaderType.PAGED)
    val imageStretchToFit = MutableStateFlow(true)
    val cropBorders = MutableStateFlow(false)
    val readProgressPage = MutableStateFlow(1)

    val upsamplingMode = MutableStateFlow(UpsamplingMode.NEAREST)
    val downsamplingKernel = MutableStateFlow(ReduceKernel.NEAREST)
    val linearLightDownsampling = MutableStateFlow(false)
    val availableUpsamplingModes = availableUpsamplingModes()
    val availableDownsamplingKernels = availableReduceKernels()

    val flashOnPageChange = MutableStateFlow(false)
    val flashDuration = MutableStateFlow(100L)
    val flashEveryNPages = MutableStateFlow(1)
    val flashWith = MutableStateFlow(ReaderFlashColor.BLACK)

    val volumeKeysNavigation = MutableStateFlow(false)
    val pixelDensity = MutableStateFlow<Density?>(null)

    suspend fun initialize(bookId: KomgaBookId) {
        upsamplingMode.value = readerSettingsRepository.getUpsamplingMode().first()
        downsamplingKernel.value = readerSettingsRepository.getDownsamplingKernel().first()
        linearLightDownsampling.value = readerSettingsRepository.getLinearLightDownsampling().first()

        imageStretchToFit.value = readerSettingsRepository.getStretchToFit().first()
        cropBorders.value = readerSettingsRepository.getCropBorders().first()
        flashOnPageChange.value = readerSettingsRepository.getFlashOnPageChange().first()
        flashDuration.value = readerSettingsRepository.getFlashDuration().first()
        flashEveryNPages.value = readerSettingsRepository.getFlashEveryNPages().first()
        flashWith.value = readerSettingsRepository.getFlashWith().first()
        volumeKeysNavigation.value = readerSettingsRepository.getVolumeKeysNavigation().first()

        appNotifications.runCatchingToNotifications {
            state.value = LoadState.Loading
            val currentBooksState = booksState.value
            if (currentBooksState == null) state.value = LoadState.Loading
            val newBook = initialBook?.takeIf { it.id == bookId }
                ?: bookApi.getOne(bookId)

            val bookPages = loadBookPages(newBook.id)

            val previous = getSibling(newBook, next = false)
            val next = getSibling(newBook, next = true)

            booksState.value = BookState(
                currentBook = newBook,
                currentBookPages = bookPages,
                previous = previous,
                next = next,
            )

            val bookProgress = newBook.readProgress
            readProgressPage.value = when {
                bookProgress == null || bookProgress.completed -> 1
                else -> bookProgress.page
            }
            currentBookId.value = bookId

            val currentSeries = runCatching { seriesApi.getOneSeries(newBook.seriesId) }.getOrNull()
            series.value = currentSeries
            readerType.value = when (currentSeries?.metadata?.readingDirection) {
                KomgaReadingDirection.LEFT_TO_RIGHT -> ReaderType.PAGED
                KomgaReadingDirection.RIGHT_TO_LEFT -> ReaderType.PAGED
                KomgaReadingDirection.WEBTOON -> ReaderType.CONTINUOUS
                KomgaReadingDirection.VERTICAL, null -> readerSettingsRepository.getReaderType().first()
            }

            state.value = LoadState.Success(Unit)
        }.onFailure { state.value = LoadState.Error(it) }

    }

    private suspend fun loadBookPages(bookId: KomgaBookId): List<PageMetadata> {
        val pages = bookApi.getBookPages(bookId)

        return pages.map {
            val width = it.width
            val height = it.height
            PageMetadata(
                bookId = bookId,
                pageNumber = it.number,
                size = if (width != null && height != null) IntSize(width, height) else null
            )
        }
    }

    private suspend fun getSibling(book: KomeliaBook, next: Boolean): SiblingLoad<ReaderSibling> = loadSibling(
        query = { queryReaderSibling(book.id, bookSiblingsContext, next, bookApi, readListApi) },
        prepare = {
            val pages = loadBookPages(it.id)
            check(pages.isNotEmpty()) { "Adjacent book has no readable image pages" }
            ReaderSibling(it, pages)
        },
    )

    suspend fun retrySibling(next: Boolean) = navigationMutex.withLock {
        val snapshot = booksState.value ?: return@withLock
        if ((if (next) snapshot.next else snapshot.previous) !is SiblingLoad.Failed) return@withLock
        retryingSibling.value = next
        try {
            val result = getSibling(snapshot.currentBook, next)
            if (booksState.value?.currentBook?.id == snapshot.currentBook.id) {
                booksState.value = if (next) snapshot.copy(next = result) else snapshot.copy(previous = result)
            }
        } finally {
            retryingSibling.value = null
        }
    }

    suspend fun loadNextBook() = navigationMutex.withLock {
        val booksState = requireNotNull(booksState.value)
        if (booksState.nextBook != null) {
            val newBook = requireNotNull(booksState.nextBook)
            val next = getSibling(newBook, next = true)

            readProgressPage.value = 1
            this.booksState.value = BookState(
                currentBook = newBook,
                currentBookPages = booksState.nextBookPages,
                previous = SiblingLoad.Available(ReaderSibling(booksState.currentBook, booksState.currentBookPages)),
                next = next,
            )
            onProgressChange(1)
        } else if (booksState.next is SiblingLoad.End) {
            navigator replace MainScreen(
                if (booksState.currentBook.oneshot) OneshotScreen(booksState.currentBook, bookSiblingsContext)
                else SeriesScreen(booksState.currentBook.seriesId)
            )
        }
    }

    suspend fun loadPreviousBook() = navigationMutex.withLock {
        val booksState = requireNotNull(booksState.value)
        if (booksState.previousBook != null) {
            val newBook = requireNotNull(booksState.previousBook)
            val previous = getSibling(newBook, next = false)

            readProgressPage.value = booksState.previousBookPages.size
            this.booksState.value = BookState(
                currentBook = newBook,
                currentBookPages = booksState.previousBookPages,
                next = SiblingLoad.Available(ReaderSibling(booksState.currentBook, booksState.currentBookPages)),
                previous = previous,
            )
        } else if (booksState.previous is SiblingLoad.End)
            appNotifications.add(AppNotification.Normal(AppNotificationMessageKey.READER_AT_BEGINNING))
    }

    suspend fun onProgressChange(page: Int) {
        readProgressPage.value = page

        if (markReadProgress) {
            appNotifications.runCatchingToNotifications {
                val currentBook = requireNotNull(booksState.value?.currentBook)
                bookApi.markReadProgress(
                    currentBook.id,
                    KomgaBookReadProgressUpdateRequest(page)
                )
            }
        }
    }

    fun onReaderTypeChange(type: ReaderType) {
        this.readerType.value = type
        stateScope.launch { readerSettingsRepository.putReaderType(type) }
    }

    fun onStretchToFitChange(stretch: Boolean) {
        imageStretchToFit.value = stretch
        stateScope.launch { readerSettingsRepository.putStretchToFit(stretch) }
    }

    fun onStretchToFitCycle() {
        val newValue = !imageStretchToFit.value
        imageStretchToFit.value = newValue
        stateScope.launch { readerSettingsRepository.putStretchToFit(newValue) }
    }

    fun onCropBordersChange(trim: Boolean) {
        cropBorders.value = trim
        stateScope.launch { readerSettingsRepository.putCropBorders(trim) }
    }

    fun onFlashEnabledChange(enabled: Boolean) {
        flashOnPageChange.value = enabled
        stateScope.launch { readerSettingsRepository.putFlashOnPageChange(enabled) }
    }

    fun onFlashDurationChange(duration: Long) {
        flashDuration.value = duration
        stateScope.launch { readerSettingsRepository.putFlashDuration(duration) }
    }

    fun onFlashEveryNPagesChange(pages: Int) {
        flashEveryNPages.value = pages
        stateScope.launch { readerSettingsRepository.putFlashEveryNPages(pages) }
    }

    fun onFlashWithChange(flashWith: ReaderFlashColor) {
        this.flashWith.value = flashWith
        stateScope.launch { readerSettingsRepository.putFlashWith(flashWith) }
    }

    fun onUpsamplingModeChange(mode: UpsamplingMode) {
        upsamplingMode.value = mode
        stateScope.launch { readerSettingsRepository.putUpsamplingMode(mode) }
    }

    fun onDownsamplingKernelChange(kernel: ReduceKernel) {
        downsamplingKernel.value = kernel
        stateScope.launch { readerSettingsRepository.putDownsamplingKernel(kernel) }
    }

    fun onLinearLightDownsamplingChange(linear: Boolean) {
        linearLightDownsampling.value = linear
        stateScope.launch { readerSettingsRepository.putLinearLightDownsampling(linear) }
    }

    fun onColorCorrectionDisable() {
        stateScope.launch {
            booksState.value?.currentBook?.let { colorCorrectionRepository.deleteSettings(it.id) }
        }
    }

    fun onDispose() {
        currentBookId.value = null
        previewLoadScope.cancel()
    }
}

@CommonParcelize
data class PageMetadata(
    val bookId: @CommonParcelizeRawValue KomgaBookId,
    val pageNumber: Int,
    val size: @CommonParcelizeRawValue IntSize?,
) : CommonParcelable {
    fun isLandscape(): Boolean {
        if (size == null) return false
        return size.width > size.height
    }

    fun toPageId() = PageId(bookId.value, pageNumber)
}

data class BookState(
    val currentBook: KomeliaBook,
    val currentBookPages: List<PageMetadata>,
    val previous: SiblingLoad<ReaderSibling>,
    val next: SiblingLoad<ReaderSibling>,
) {
    val previousBook: KomeliaBook? = (previous as? SiblingLoad.Available)?.value?.book
    val previousBookPages: List<PageMetadata> = (previous as? SiblingLoad.Available)?.value?.pages.orEmpty()
    val nextBook: KomeliaBook? = (next as? SiblingLoad.Available)?.value?.book
    val nextBookPages: List<PageMetadata> = (next as? SiblingLoad.Available)?.value?.pages.orEmpty()
}
