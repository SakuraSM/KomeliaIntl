package snd.komelia.ui.reader.image.panels

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.image.ReaderImage
import snd.komelia.image.ReaderImagePrefetch
import snd.komelia.image.ReaderPrefetchBudget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.image.BookImageLoader
import snd.komelia.image.ImageRect
import snd.komelia.image.KomeliaPanelDetector
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.ReaderImageResult
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.PagedReadingDirection.LEFT_TO_RIGHT
import snd.komelia.settings.model.PagedReadingDirection.RIGHT_TO_LEFT
import snd.komelia.ui.reader.image.BookState
import snd.komelia.ui.reader.image.PageMetadata
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ScreenScaleState
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage
import snd.komelia.ui.reader.image.paged.RetainedPageCache
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookEnd
import snd.komelia.ui.reader.image.paged.PagedReaderState.TransitionPage.BookStart
import snd.komga.client.common.KomgaReadingDirection
import kotlin.math.roundToInt
import kotlin.time.measureTimedValue

private val logger = KotlinLogging.logger { }
private const val PREFETCH_SETTLE_MILLIS = 180L
private const val PREFETCH_RETRY_MILLIS = 250L
private const val PREFETCH_ATTEMPTS = 3

class PanelsReaderState(
    private val cleanupScope: CoroutineScope,
    private val settingsRepository: ImageReaderSettingsRepository,
    private val appNotifications: AppNotifications,
    internal val readerState: ReaderState,
    private val imageLoader: BookImageLoader,
    private val pageChangeFlow: MutableSharedFlow<Unit>,
    private val onnxRuntimeRfDetr: KomeliaPanelDetector,
    val screenScaleState: ScreenScaleState,
) {
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var displayedBookId: snd.komga.client.book.KomgaBookId? = null
    private val pageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val imageLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var imageCache = newImageCache()

    private fun newImageCache() = RetainedPageCache<PageId, PanelsPage>(imageLoadScope) { page ->
        cleanupScope.launch { page.imageResult?.image?.close() }
    }

    val pageMetadata: MutableStateFlow<List<PageMetadata>> = MutableStateFlow(emptyList())

    val currentPageIndex = MutableStateFlow(PageIndex(0, 0, false))
    val currentPage: MutableStateFlow<PanelsPage?> = MutableStateFlow(null)
    val transitionPage: MutableStateFlow<TransitionPage?> = MutableStateFlow(null)
    val readingDirection = MutableStateFlow(LEFT_TO_RIGHT)
    val prerenderCount = MutableStateFlow(1)
    private val settingsWriteMutex = Mutex()
    private val pendingPrerenderCount = MutableStateFlow<Int?>(null)
    private val detectionMutex = Mutex()
    private val stopped = MutableStateFlow(false)
    private var renderBudget = ReaderPrefetchBudget()
    private var prefetchedImages = emptySet<ReaderImage>()

    @OptIn(FlowPreview::class)
    suspend fun initialize() {
        imageCache.close()
        imageCache = newImageCache()
        renderBudget = ReaderPrefetchBudget()
        prerenderCount.value = pendingPrerenderCount.value ?: settingsRepository.getPanelPrerenderCount().first().coerceIn(0, 2)
        stopped.value = false
        readingDirection.value = when (readerState.series.value?.metadata?.readingDirection) {
            KomgaReadingDirection.LEFT_TO_RIGHT -> LEFT_TO_RIGHT
            KomgaReadingDirection.RIGHT_TO_LEFT -> RIGHT_TO_LEFT
            else -> settingsRepository.getPagedReaderReadingDirection().first()
        }

        screenScaleState.setScrollState(null)
        screenScaleState.setScrollOrientation(Orientation.Vertical, false)

        combine(
            screenScaleState.transformation,
            screenScaleState.areaSize,
        ) {}
            .drop(1).conflate()
            .onEach {
                currentPage.value?.let { page ->
                    updateImageState(page, screenScaleState)
                    delay(100)
                }
            }
            .launchIn(stateScope)

        readingDirection.drop(1).onEach { readingDirection ->
            val page = currentPage.value
            val panelData = page?.panelData
            if (panelData != null) {
                val sortedPanels = sortPanels(
                    panels = panelData.panels,
                    imageSize = panelData.originalImageSize,
                    readingDirection = readingDirection
                )
                currentPage.value = page.copy(panelData = panelData.copy(panels = sortedPanels))
                currentPageIndex.update { it.copy(panel = 0, isLastPanelZoomOutActive = false) }

                if (sortedPanels.isNotEmpty()) {
                    scrollToPanel(
                        imageSize = page.panelData.originalImageSize,
                        screenSize = screenScaleState.areaSize.value,
                        targetSize = screenScaleState.targetSize.value.toIntSize(),
                        panel = sortedPanels.first()
                    )
                }

            }

        }.launchIn(stateScope)

        merge(
            currentPage.map { Unit }, currentPageIndex.map { Unit },
            screenScaleState.areaSize.map { Unit }, screenScaleState.transformation.map { Unit },
            prerenderCount.map { Unit }, readingDirection.map { Unit },
            readerState.imageStretchToFit.map { Unit }, readerState.cropBorders.map { Unit },
            readerState.upsamplingMode.map { Unit }, readerState.downsamplingKernel.map { Unit },
            readerState.linearLightDownsampling.map { Unit },
        ).debounce(PREFETCH_SETTLE_MILLIS).let { changes ->
            stateScope.launch { changes.collectLatest { prepareUpcomingPanels() } }
        }

        readerState.booksState
            .filterNotNull()
            .onEach { newBook -> onNewBookLoaded(newBook) }
            .launchIn(stateScope)
    }

    fun stop() {
        stopped.value = true
        displayedBookId = null
        stateScope.coroutineContext.cancelChildren()
        prefetchedImages.forEach { it.clearPrefetch() }
        prefetchedImages = emptySet()
        pageLoadScope.coroutineContext.cancelChildren()
        imageCache.close()
        imageLoadScope.coroutineContext.cancelChildren()
        screenScaleState.enableOverscrollArea(false)
    }

    private suspend fun updateImageState(page: PanelsPage, scale: ScreenScaleState) {
        val viewport = page.viewport(scale, readerState.imageStretchToFit.value) ?: return
        page.imageResult?.image?.requestUpdate(viewport.maxDisplaySize, viewport.zoomFactor, viewport.visibleDisplaySize)
    }

    fun onPrerenderCountChange(count: Int) {
        prerenderCount.value = count.coerceIn(0, 2)
        pendingPrerenderCount.value = prerenderCount.value
        prefetchedImages.forEach { it.clearPrefetch() }
        val index = currentPageIndex.value.page
        val metadata = pageMetadata.value
        if (!stopped.value && index in metadata.indices) {
            imageCache.retain(panelPreloadRange(index, metadata.size, prerenderCount.value).map { metadata[it].toPageId() }.toSet())
        }
        cleanupScope.launch {
            settingsWriteMutex.withLock {
                while (true) {
                    val desired = pendingPrerenderCount.value ?: break
                    try {
                        settingsRepository.putPanelPrerenderCount(desired)
                    } catch (error: Throwable) {
                        currentCoroutineContext().ensureActive()
                        appNotifications.addErrorNotification(error)
                        break
                    }
                    pendingPrerenderCount.compareAndSet(desired, null)
                }
            }
        }
    }

    private suspend fun prepareUpcomingPanels() {
        if (stopped.value) return
        val count = prerenderCount.value
        val activePage = currentPage.value ?: return
        val index = currentPageIndex.value
        val metadata = pageMetadata.value
        val area = screenScaleState.areaSize.value
        if (area.width <= 0 || area.height <= 0 || index.page !in metadata.indices) return
        if (count == 0) {
            prefetchedImages.forEach { it.clearPrefetch() }
            prefetchedImages = emptySet()
            return
        }
        val retained = panelPreloadRange(index.page, metadata.size, count)
        imageCache.retain(retained.map { metadata[it].toPageId() }.toSet())
        val pages = mutableMapOf(index.page to activePage)
        // Only await a following page when the lookahead can actually cross a page boundary.
        val remaining = activePage.panelData?.panels?.size?.minus(index.panel + 1)?.coerceAtLeast(0) ?: 0
        if (remaining < count) {
            for (pageIndex in (index.page + 1)..(index.page + count).coerceAtMost(metadata.lastIndex)) {
                val page = launchDownload(metadata[pageIndex]).await()
                pages[pageIndex] = page.withSortedPanels()
            }
        }
        val targets = upcomingPanelViews(index, pages.mapValues { it.value.panelData }, count)
        val grouped = targets.groupBy { pages.getValue(it.page).imageResult?.image }
        val nextImages = grouped.keys.filterNotNull().toSet()
        prefetchedImages.filter { it !in nextImages && it !== activePage.imageResult?.image }.forEach { it.clearPrefetch() }
        prefetchedImages = nextImages
        for ((image, views) in grouped) {
            if (image == null) continue
            val viewports = views.mapNotNull { target ->
                val page = pages.getValue(target.page)
                page.viewport(page.scaleForPanel(area, target.panel, readerState.imageStretchToFit.value), readerState.imageStretchToFit.value)
            }
            for (attempt in 0 until PREFETCH_ATTEMPTS) {
                currentCoroutineContext().ensureActive()
                val prepared = try { image.prefetch(ReaderImagePrefetch(viewports, renderBudget)) }
                catch (error: Throwable) {
                    currentCoroutineContext().ensureActive()
                    logger.debug { "Panel pre-render skipped: ${error::class.simpleName}" }
                    break
                }
                if (prepared >= viewports.size) break
                delay(PREFETCH_RETRY_MILLIS)
            }
        }
    }

    private fun PanelsPage.withSortedPanels(): PanelsPage {
        val metadata = panelData ?: return this
        return copy(panelData = metadata.copy(panels = sortPanels(metadata.panels, metadata.originalImageSize, readingDirection.value)))
    }

    private fun onNewBookLoaded(bookState: BookState) {
        if (displayedBookId == bookState.currentBook.id) {
            transitionPage.value = when (val page = transitionPage.value) {
                is BookEnd -> page.copy(nextBook = bookState.nextBook)
                is BookStart -> page.copy(previousBook = bookState.previousBook)
                null -> null
            }
            return
        }
        displayedBookId = bookState.currentBook.id
        val newPages = bookState.currentBookPages
        val newPageIndex = readerState.readProgressPage.value - 1

        pageMetadata.value = bookState.currentBookPages
        currentPage.value = PanelsPage(
            metadata = newPages[newPageIndex],
            imageResult = null,
            panelData = null
        )
        currentPageIndex.value = PageIndex(newPageIndex, 0, false)

        launchPageLoad(newPageIndex)
    }

    fun onReadingDirectionChange(readingDirection: PagedReadingDirection) {
        this.readingDirection.value = readingDirection
        stateScope.launch { settingsRepository.putPagedReaderReadingDirection(readingDirection) }
    }


    fun nextPanel() {
        val pageIndex = currentPageIndex.value
        val currentPage = currentPage.value
        if (currentPage == null || currentPage.panelData == null) {
            nextPage()
            return
        }
        val panelData = currentPage.panelData
        val panels = panelData.panels
        val panelIndex = pageIndex.panel

        if (panels.size <= panelIndex + 1) {
            if (panels.isEmpty() || panelData.panelCoversMajorityOfImage || pageIndex.isLastPanelZoomOutActive) {
                nextPage()
            } else {
                scrollToFit()
                currentPageIndex.update { it.copy(isLastPanelZoomOutActive = true) }
            }
            return
        }
        val nextPanel = panels[panelIndex + 1]
        val areaSize = screenScaleState.areaSize.value
        val targetSize = IntSize(
            screenScaleState.targetSize.value.width.roundToInt(),
            screenScaleState.targetSize.value.height.roundToInt()
        )
        val imageSize = currentPage.panelData.originalImageSize
        scrollToPanel(
            imageSize = imageSize,
            screenSize = areaSize,
            targetSize = targetSize,
            panel = nextPanel
        )
        currentPageIndex.update { it.copy(panel = panelIndex + 1) }
    }

    private fun nextPage() {
        val currentPageIndex = currentPageIndex.value.page
        val currentTransitionPage = transitionPage.value
        when {
            currentPageIndex < pageMetadata.value.size - 1 -> {
                if (currentTransitionPage != null) this.transitionPage.value = null
                else onPageChange(currentPageIndex + 1)
            }

            currentTransitionPage == null -> {
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookEnd(
                    currentBook = bookState.currentBook,
                    nextBook = bookState.nextBook
                )
            }

            currentTransitionPage is BookEnd && currentTransitionPage.nextBook != null -> {
                stateScope.launch {
                    currentPage.value = null
                    transitionPage.value = null
                    readerState.loadNextBook()
                }
            }
        }
    }

    fun previousPanel() {
        val pageIndex = currentPageIndex.value
        val currentPage = currentPage.value
        if (currentPage == null || currentPage.panelData == null) {
            previousPage()
            return
        }
        val panels = currentPage.panelData.panels
        val panelIndex = pageIndex.panel

        if (panelIndex - 1 < 0) {
            previousPage()
            return
        }
        val previousPage = panels[panelIndex - 1]
        val areaSize = screenScaleState.areaSize.value
        val targetSize = IntSize(
            screenScaleState.targetSize.value.width.roundToInt(),
            screenScaleState.targetSize.value.height.roundToInt()
        )
        val imageSize = currentPage.panelData.originalImageSize
        scrollToPanel(
            imageSize = imageSize,
            screenSize = areaSize,
            targetSize = targetSize,
            panel = previousPage
        )
        currentPageIndex.update {
            it.copy(panel = panelIndex - 1, isLastPanelZoomOutActive = false)
        }
    }

    private fun previousPage() {
        val currentPgeIndex = currentPageIndex.value.page
        val currentTransitionPage = transitionPage.value
        when {
            currentPgeIndex != 0 -> {
                if (currentTransitionPage != null) this.transitionPage.value = null
                else onPageChange(currentPgeIndex - 1)
            }

            currentTransitionPage == null -> {
                val bookState = readerState.booksState.value ?: return
                this.transitionPage.value = BookStart(
                    currentBook = bookState.currentBook,
                    previousBook = bookState.previousBook
                )
            }

            currentTransitionPage is BookStart && currentTransitionPage.previousBook != null -> {
                stateScope.launch {
                    currentPage.value = null
                    transitionPage.value = null
                    readerState.loadPreviousBook()
                }
            }
        }
    }

    fun onPageChange(page: Int) {
        if (currentPageIndex.value.page == page) return
        pageChangeFlow.tryEmit(Unit)
        launchPageLoad(page)
    }

    fun retryPage(page: PageMetadata) {
        imageCache.invalidate(page.toPageId())
        val pageIndex = pageMetadata.value.indexOf(page)
        if (pageIndex >= 0) {
            pageChangeFlow.tryEmit(Unit)
            launchPageLoad(pageIndex)
        }
    }

    private fun launchPageLoad(pageIndex: Int) {
        if (stopped.value) return
        if (pageIndex != currentPageIndex.value.page) {
            val pageNumber = pageIndex + 1
            stateScope.launch { readerState.onProgressChange(pageNumber) }
        }

        pageLoadScope.coroutineContext.cancelChildren()
        pageLoadScope.launch { doPageLoad(pageIndex) }
    }

    private suspend fun doPageLoad(pageIndex: Int) {
        val metadata = pageMetadata.value
        imageCache.retain(panelPreloadRange(pageIndex, metadata.size, prerenderCount.value)
            .map { metadata[it].toPageId() }.toSet())
        val pageMeta = metadata[pageIndex]
        val downloadJob = launchDownload(pageMeta)
        preloadImagesBetween(pageIndex)

        if (downloadJob.isActive) {
            currentPage.value = PanelsPage(
                metadata = pageMeta,
                imageResult = null,
                panelData = null
            )
            currentPageIndex.update { PageIndex(pageIndex, 0, false) }
            transitionPage.value = null
            screenScaleState.enableOverscrollArea(false)
            screenScaleState.setZoom(0f)
        }

        val page = downloadJob.await()
        val sortedPanelsPage = if (page.panelData != null) {
            val sortedPanels = sortPanels(
                page.panelData.panels,
                page.panelData.originalImageSize,
                readingDirection.value
            )
            page.copy(panelData = page.panelData.copy(panels = sortedPanels))
        } else page

        val containerSize = screenScaleState.areaSize.value
        val scale = getScaleFor(sortedPanelsPage, containerSize)
        updateImageState(sortedPanelsPage, scale)
        currentCoroutineContext().ensureActive()
        currentPageIndex.update { PageIndex(pageIndex, 0, false) }
        transitionPage.value = null
        logger.info { "current page value $sortedPanelsPage" }
        currentPage.value = sortedPanelsPage
        screenScaleState.enableOverscrollArea(true)
        screenScaleState.apply(scale)
    }

    private fun preloadImagesBetween(pageIndex: Int) {
        val metadata = pageMetadata.value
        panelPreloadRange(pageIndex, metadata.size, prerenderCount.value).filter { it != pageIndex }
            .forEach { launchDownload(metadata[it]) }
    }

    private fun launchDownload(meta: PageMetadata): Deferred<PanelsPage> =
        imageCache.getOrLoad(meta.toPageId(), acceptCached = { it.imageResult !is ReaderImageResult.Error }) {
            loadPanelsPage(meta)
        }

    private suspend fun loadPanelsPage(meta: PageMetadata): PanelsPage {
        val imageResult = imageLoader.loadReaderImage(meta.bookId, meta.pageNumber)
        val image = imageResult.image ?: return PanelsPage(meta, imageResult, null)
        try {
            val original = image.getOriginalImage()
            val originalImage = original.getOrNull()
            if (originalImage == null) {
                image.close()
                return PanelsPage(meta, ReaderImageResult.Error(checkNotNull(original.exceptionOrNull())), null)
            }
            currentCoroutineContext().ensureActive()
            val imageSize = IntSize(originalImage.width, originalImage.height)
            val (panels, duration) = detectionMutex.withLock {
                currentCoroutineContext().ensureActive()
                measureTimedValue { onnxRuntimeRfDetr.detect(originalImage).map { it.boundingBox } }
            }
            // A synchronous native detector may finish after cancellation. Its image remains
            // owned until this check, and is released by the failure path before returning.
            currentCoroutineContext().ensureActive()
            logger.info { "page ${meta.pageNumber} panel detection completed in $duration" }
            val panelsArea = areaOfRects(panels.map { it.toRect() })
            val imageArea = originalImage.width.toFloat() * originalImage.height
            val untrimmedRatio = panelsArea / imageArea
            val panelRatio = if (untrimmedRatio < .8f) {
                val trim = originalImage.findTrim()
                panelsArea / (trim.width.toFloat() * trim.height).coerceAtLeast(1f)
            } else untrimmedRatio
            return PanelsPage(meta, imageResult, PanelData(panels, imageSize, panelRatio > .8f))
        } catch (error: Throwable) {
            image.close()
            currentCoroutineContext().ensureActive()
            return PanelsPage(meta, ReaderImageResult.Error(error), null)
        }
    }

    private suspend fun getScaleFor(page: PanelsPage, containerSize: IntSize): ScreenScaleState =
        page.scaleForPanel(containerSize, 0, readerState.imageStretchToFit.value)

    private fun scrollToFit() {
//        val areaSize = screenScaleState.areaSize.value
//        val startX = 0 - areaSize.width.toFloat()
//        val startY = 0 - areaSize.height.toFloat()
        screenScaleState.setZoom(0f)
        screenScaleState.scrollTo(Offset(0f, 0f))

    }

    private fun scrollToPanel(
        imageSize: IntSize,
        screenSize: IntSize,
        targetSize: IntSize,
        panel: ImageRect,
    ) {
        val (offset, zoom) = panelOffsetAndZoom(PanelViewportGeometry(imageSize, screenSize, targetSize, panel))
        screenScaleState.setZoom(zoom)
        screenScaleState.scrollTo(offset)
    }

    data class PanelsPage(
        val metadata: PageMetadata,
        val imageResult: ReaderImageResult?,
        val panelData: PanelData?,
    )

    data class PanelData(
        val panels: List<ImageRect>,
        val originalImageSize: IntSize,
        val panelCoversMajorityOfImage: Boolean,
    )

    data class PageIndex(
        val page: Int,
        val panel: Int,
        val isLastPanelZoomOutActive: Boolean,
    )

}
