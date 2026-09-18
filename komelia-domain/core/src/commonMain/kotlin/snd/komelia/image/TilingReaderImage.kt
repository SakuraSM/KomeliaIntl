package snd.komelia.image

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toRect
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.image.processing.ImageProcessingPipeline
import kotlin.concurrent.Volatile
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlin.time.measureTime

private const val tileThreshold1 = 2048 * 2048
private const val tileThreshold2 = 4096 * 4096
private const val tileThreshold3 = 6144 * 6144


expect class RenderImage

private val logger = KotlinLogging.logger {}

abstract class TilingReaderImage(
    private val imageSource: ImageSource,
    private val imageDecoder: KomeliaImageDecoder,
    private val processingPipeline: ImageProcessingPipeline,
    private val stretchImages: StateFlow<Boolean>,
    protected val upsamplingMode: StateFlow<UpsamplingMode>,
    protected val downSamplingKernel: StateFlow<ReduceKernel>,
    protected val linearLightDownSampling: StateFlow<Boolean>,
    final override val pageId: ReaderImage.PageId,
    processingDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : ReaderImage {
    final override val painter = MutableStateFlow<TiledPainter?>(null)
    final override val error = MutableStateFlow<Throwable?>(null)

    final override val originalSize = MutableStateFlow<IntSize?>(null)
    final override val displaySize = MutableStateFlow<IntSize?>(null)
    final override val currentSize = MutableStateFlow<IntSize?>(null)

    private val imageAwaitScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val animationScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())
    protected val processingScope = CoroutineScope(processingDispatcher + SupervisorJob())
    private val imageMutex = Mutex()
    private val closed = MutableStateFlow(false)
    private val visibleRequestSerial = MutableStateFlow(0L)
    private val prefetchSerial = MutableStateFlow(0L)
    private val renderRevision = MutableStateFlow(0L)
    private var activeRenderSerial = 0L
    private var activeRenderRevision = 0L
    private val latestVisibleRequest = MutableStateFlow<UpdateRequest?>(null)
    private var completedVisibleRequest: UpdateRequest? = null
    // Float rounding in animation must not prevent reuse of identical rendered pixels.
    private data class PrefetchKey(
        val serial: Long,
        val displaySize: IntSize,
        val pixelSize: IntSize,
        val regions: List<Pair<IntRect, IntSize>>,
    )
    private val preparedFrames = PreparedFrameCache<PrefetchKey, FrameData> { frame ->
        closeTileBitmaps(frame.allTiles())
    }

    private val jobFlow = MutableSharedFlow<UpdateRequest>(1, 0, BufferOverflow.DROP_OLDEST)
    private val frameData = MutableStateFlow<FrameData?>(null)
    protected val image = MutableStateFlow<KomeliaImage?>(null)
    protected val defaultFrameDelay = 100L

    @Volatile
    private var originalImage: KomeliaImage? = null

    @Volatile
    protected var lastUpdateRequest: UpdateRequest? = null

    @Volatile
    protected var lastUsedScaleFactor: Double? = null

    data class UpdateRequest(
        val visibleDisplaySize: IntRect,
        val zoomFactor: Float,
        val maxDisplaySize: IntSize,
    )

    init {
        jobFlow.conflate()
            .onEach { request ->
                try {
                    doUpdate(request)
                    this.error.value = null
                } catch (_: ForegroundSuperseded) {
                    // A newer viewport is already queued; keep the current painter until it wins.
                } catch (e: Throwable) {
                    currentCoroutineContext().ensureActive()
                    logger.catching(e)
                    this.error.value = e
                }

                delay(50)
            }.launchIn(processingScope)

        processingPipeline.changeFlow.onEach {
            prefetchSerial.update { it + 1 }
            renderRevision.update { it + 1 }
            // Native calls can switch dispatchers and suspend. limitedParallelism(1) alone
            // does not keep a crop reload from closing an image that a resize still uses.
            imageMutex.withLock {
                preparedFrames.clear()
                completedVisibleRequest = null
                releaseImages()
                originalSize.value = null
                currentSize.value = null
                loadImage()
            }
            reloadLastRequest()
        }.launchIn(processingScope)

        stretchImages.drop(1).onEach { reloadLastRequest() }.launchIn(processingScope)
        upsamplingMode.onEach { mode -> onUpsamplingModeChanged(mode) }
            .launchIn(processingScope)
        downSamplingKernel.drop(1).onEach { reloadLastRequest() }
            .launchIn(processingScope)
        linearLightDownSampling.drop(1).onEach { reloadLastRequest() }
            .launchIn(processingScope)

        processingScope.launch { imageMutex.withLock { loadImage() } }

        frameData.onEach { data ->
            when {
                data == null -> this.painter.value = null
                data.frames.size == 1 -> {
                    this.painter.value = createTilePainter(
                        tiles = listOfNotNull(data.fallback) + data.frames.first().tiles,
                        displaySize = data.displaySize,
                        scaleFactor = data.scaleFactor
                    )
                }

                else -> launchAnimation(data)
            }

        }.launchIn(processingScope)
    }

    private fun launchAnimation(data: FrameData) {
        animationScope.coroutineContext.cancelChildren()
        animationScope.launch {
            val painters = data.frames.map {
                createTilePainter(
                    tiles = it.tiles,
                    displaySize = data.displaySize,
                    scaleFactor = data.scaleFactor
                )
            }

            while (isActive) {
                for ((index, tiledPainter) in painters.withIndex()) {
                    val frameDelay = data.frames[index].delay
                    this@TilingReaderImage.painter.value = tiledPainter
                    delay(if (frameDelay < 10) defaultFrameDelay else frameDelay)
                }
            }
        }
    }

    protected suspend fun reloadLastRequest() {
        prefetchSerial.update { it + 1 }
        renderRevision.update { it + 1 }
        imageMutex.withLock {
            if (closed.value) return@withLock
            preparedFrames.clear()
            completedVisibleRequest = null
            (latestVisibleRequest.value ?: lastUpdateRequest)?.let { lastRequest ->
                lastUsedScaleFactor = null
                visibleRequestSerial.update { it + 1 }
                latestVisibleRequest.value = lastRequest
                jobFlow.emit(lastRequest)
            }
        }
    }

    protected open suspend fun onUpsamplingModeChanged(mode: UpsamplingMode) {
        clearPrefetch()
        painter.update { it?.withSamplingMode(mode) }
    }

    protected open fun sourceTileSize(tileSize: Int, scaleFactor: Double): Int = tileSize

    override suspend fun getOriginalImageSize(): Result<IntSize> {
        return coroutineScope {
            select {
                async { originalSize.filterNotNull().first() }.onAwait { Result.success(it) }
                async { error.filterNotNull().first() }.onAwait { Result.failure(it) }
            }.also { coroutineContext.cancelChildren() }
        }
    }

    override suspend fun getOriginalImage(): Result<KomeliaImage> {
        return coroutineScope {
            select {
                async { image.filterNotNull().first() }.onAwait { Result.success(it) }
                async { error.filterNotNull().first() }.onAwait { Result.failure(it) }
            }.also { coroutineContext.cancelChildren() }
        }
    }

    private suspend fun loadImage() {
        try {
            val originalImage = decodeImage(imageSource)
            this.originalImage = originalImage
            val processed = processingPipeline.process(pageId, originalImage)
            image.value = processed
            originalSize.value = IntSize(processed.width, processed.pageHeight)
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.catching(e)
            this.error.value = e
            imageAwaitScope.coroutineContext.cancelChildren()
        }
    }

    private suspend fun decodeImage(source: ImageSource): KomeliaImage {
        val image = when (source) {
            is ImageSource.FilePathSource -> imageDecoder.decodeFromFile(source.path)
            is ImageSource.MemorySource -> imageDecoder.decode(source.data)
        }
        return if (image.pagesTotal != 1) {
            image.close()
            when (source) {
                is ImageSource.FilePathSource -> imageDecoder.decodeFromFile(source.path, -1)
                is ImageSource.MemorySource -> imageDecoder.decode(source.data, -1)
            }
        } else {
            image
        }

    }

    private suspend fun getCurrentImage(): KomeliaImage {
        return imageAwaitScope.async { image.filterNotNull().first() }.await()
    }

    override fun requestUpdate(
        maxDisplaySize: IntSize,
        zoomFactor: Float,
        visibleDisplaySize: IntRect,
    ) {
        val request = UpdateRequest(visibleDisplaySize, zoomFactor, maxDisplaySize)
        if (latestVisibleRequest.value != request) visibleRequestSerial.update { it + 1 }
        latestVisibleRequest.value = request
        jobFlow.tryEmit(request)
    }

    private suspend fun doUpdate(request: UpdateRequest) {
        lastUpdateRequest = request

        // Wait outside the lock so the initial decoder can publish the first image.
        getCurrentImage()
        imageMutex.withLock {
            // A reload may have replaced the image while we were waiting for the lock.
            val currentImage = image.value ?: return@withLock
            if (latestVisibleRequest.value != request) return@withLock
            activeRenderSerial = visibleRequestSerial.value
            activeRenderRevision = renderRevision.value
            doUpdate(request, currentImage)
            completedVisibleRequest = request
        }
    }

    private data class RenderPlan(
        val displaySize: IntSize,
        val displayScale: Double,
        val scale: Double,
        val pixelSize: IntSize,
        val tileSize: Int?,
    )

    private suspend fun renderPlan(request: UpdateRequest, image: KomeliaImage): RenderPlan? {
        if (request.maxDisplaySize.width <= 0 || request.maxDisplaySize.height <= 0 ||
            !request.zoomFactor.isFinite() || request.zoomFactor <= 0f) return null
        val display = calculateSizeForArea(request.maxDisplaySize, stretchImages.value) ?: return null
        val displayScale = minOf(display.width.toDouble() / image.width, display.height.toDouble() / image.pageHeight)
        val scale = displayScale * request.zoomFactor
        val pixels = IntSize((display.width * request.zoomFactor).roundToInt(), (display.height * request.zoomFactor).roundToInt())
        val count = pixels.width.toLong() * pixels.height
        val tileSize = when {
            count <= tileThreshold1 -> null
            count <= tileThreshold2 -> 1024
            count <= tileThreshold3 -> 512
            else -> 256
        }
        return RenderPlan(display, displayScale, scale, pixels, tileSize?.let { sourceTileSize(it, scale) })
    }

    private fun tilesFor(plan: RenderPlan, request: UpdateRequest, image: KomeliaImage): List<ViewportTile> =
        viewportTiles(ViewportTilePlan(IntSize(image.width, image.height), checkNotNull(plan.tileSize),
            plan.displayScale, plan.scale, request.visibleDisplaySize.toRect()))

    private fun prefetchKey(generation: Long, plan: RenderPlan, request: UpdateRequest, image: KomeliaImage): PrefetchKey =
        PrefetchKey(generation, plan.displaySize, plan.pixelSize,
            if (plan.tileSize == null) emptyList() else tilesFor(plan, request, image).map { it.source to it.pixels })

    private suspend fun doUpdate(request: UpdateRequest, image: KomeliaImage) {
        val plan = renderPlan(request, image) ?: return
        displaySize.value = plan.displaySize
        currentSize.value = plan.pixelSize
        val prepared = preparedFrames.take(prefetchKey(prefetchSerial.value, plan, request, image))
        if (prepared != null) {
            if (prepared.sourceImage === image) {
                try {
                    if (plan.tileSize != null) ensureTileFallback(image, plan.displaySize, plan.scale)
                    ensureForegroundCurrent()
                    publishFrame(FrameData(prepared.frames, prepared.displaySize, prepared.scaleFactor, image,
                        frameData.value?.takeIf { it.sourceImage === image }?.fallback))
                    lastUsedScaleFactor = plan.scale
                    logger.debug { "page ${pageId.pageNumber} reused pre-rendered viewport" }
                } catch (error: Throwable) {
                    if (frameData.value?.frames !== prepared.frames) closeTileBitmaps(prepared.allTiles())
                    throw error
                }
                return
            }
            closeTileBitmaps(prepared.allTiles())
        }
        if (image.pagesLoaded > 1 || plan.tileSize == null) {
            doFullResize(image, plan.scale, plan.displayScale, plan.displaySize)
        } else {
            doTile(image, request.visibleDisplaySize.toRect(), plan.displayScale, plan.scale, plan.displaySize, plan.tileSize)
        }
    }

    override fun clearPrefetch() {
        prefetchSerial.update { it + 1 }
        processingScope.launch { imageMutex.withLock { preparedFrames.clear() } }
    }

    override suspend fun prefetch(request: ReaderImagePrefetch): Int {
        if (closed.value) return 0
        val generation = prefetchSerial.value
        val foreground = visibleRequestSerial.value
        val targets = request.viewports.distinct().take(2).map {
            UpdateRequest(it.visibleDisplaySize, it.zoomFactor, it.maxDisplaySize)
        }
        val task = processingScope.async {
            if (targets.isEmpty()) {
                imageMutex.withLock { preparedFrames.clear() }
                return@async 0
            }
            getCurrentImage()
            imageMutex.withLock {
                val currentImage = image.value ?: return@withLock 0
                if (closed.value || currentImage.pagesLoaded > 1 || currentImage.pagesTotal > 1) return@withLock 0
                if (latestVisibleRequest.value != completedVisibleRequest) return@withLock 0
                val keyedTargets = targets.mapNotNull { target ->
                    renderPlan(target, currentImage)?.let { prefetchKey(generation, it, target, currentImage) to target }
                }
                preparedFrames.retain(keyedTargets.map { it.first }.toSet())
                for ((key, target) in keyedTargets) {
                    currentCoroutineContext().ensureActive()
                    if (prefetchSerial.value != generation || visibleRequestSerial.value != foreground) break
                    if (preparedFrames.contains(key) || completedVisibleRequest == target) continue
                    prepareFrame(target, currentImage, request.budget, generation, foreground)?.let { (frame, reservation) ->
                        preparedFrames.put(key, frame, reservation)
                    }
                }
                keyedTargets.count { (key, target) -> preparedFrames.contains(key) || target == completedVisibleRequest }
            }
        }
        try { return task.await() }
        finally { if (!currentCoroutineContext().isActive) task.cancel() }
    }

    private suspend fun prepareFrame(
        request: UpdateRequest,
        image: KomeliaImage,
        budget: ReaderPrefetchBudget,
        generation: Long,
        foreground: Long,
    ): Pair<FrameData, ReaderPrefetchBudget.Reservation>? {
        val plan = renderPlan(request, image) ?: return null
        val specs = if (plan.tileSize != null) tilesFor(plan, request, image) else listOf(
            ViewportTile(IntRect(0, 0, image.width, image.pageHeight),
                Rect(0f, 0f, plan.displaySize.width.toFloat(), plan.displaySize.height.toFloat()), plan.pixelSize))
        var bytes = 0L
        for (spec in specs) {
            val cost = rgbaPixelBytes(spec.pixels)
            if (cost > budget.maximumBytes - bytes) return null
            bytes += cost
        }
        if (specs.isEmpty()) return null
        val reservation = budget.reserve(bytes) ?: return null
        val tiles = mutableListOf<ReaderImageTile>()
        try {
            for (spec in specs) {
                currentCoroutineContext().ensureActive()
                if (prefetchSerial.value != generation || visibleRequestSerial.value != foreground) throw PrefetchSuperseded()
                // A native resize cannot be interrupted safely after allocating pixels.
                // Register ownership before observing cancellation and retiring this batch.
                withContext(NonCancellable) {
                    val pixels = if (plan.tileSize == null) resizeImage(image, spec.pixels.width, spec.pixels.height)
                    else getImageRegion(image, spec.source, spec.pixels.width, spec.pixels.height)
                    tiles.add(ReaderImageTile(IntSize(pixels.width, pixels.height), spec.display, true, pixels.frames.single()))
                }
                currentCoroutineContext().ensureActive()
                if (prefetchSerial.value != generation || visibleRequestSerial.value != foreground) throw PrefetchSuperseded()
            }
            return FrameData(listOf(ImageFrame(tiles, 0)), plan.displaySize, plan.scale, image, null) to reservation
        } catch (error: Throwable) {
            closeTileBitmaps(tiles)
            reservation.release()
            if (error is PrefetchSuperseded) return null
            throw error
        }
    }

    private class PrefetchSuperseded : CancellationException("Foreground viewport superseded pre-rendering")

    private class ForegroundSuperseded : CancellationException("Newer image viewport requested")

    private suspend fun ensureForegroundCurrent() {
        currentCoroutineContext().ensureActive()
        if (activeRenderSerial != visibleRequestSerial.value || activeRenderRevision != renderRevision.value) {
            throw ForegroundSuperseded()
        }
    }

    private suspend fun doFullResize(
        image: KomeliaImage,
        scaleFactor: Double,
        displayScaleFactor: Double,
        displayArea: IntSize
    ) {
        if (lastUsedScaleFactor == scaleFactor) {
            error.value?.let { throw (it) }
            return
        }

        val dstWidth = (image.width * scaleFactor).roundToInt()
        val dstHeight = (image.pageHeight * scaleFactor).roundToInt()

        measureTime {
            val resizedImage = resizeImage(
                image,
                dstWidth,
                dstHeight,
            )
            val frames = resizedImage.frames.mapIndexed { i, renderImage ->
                ImageFrame(
                    tiles = listOf(
                        ReaderImageTile(
                            size = IntSize(resizedImage.width, resizedImage.height),
                            displayRegion = Rect(
                                0f,
                                0f,
                                round(image.width * displayScaleFactor).toFloat(),
                                round(image.pageHeight * displayScaleFactor).toFloat()
                            ),
                            isVisible = true,
                            renderImage = renderImage
                        )
                    ),
                    delay = resizedImage.delays?.getOrNull(i) ?: defaultFrameDelay
                )
            }
            try {
                ensureForegroundCurrent()
                publishFrame(FrameData(
                frames = frames,
                displaySize = displayArea,
                scaleFactor = scaleFactor,
                sourceImage = image,
                fallback = frameData.value?.takeIf { it.sourceImage === image }?.fallback?.copy(
                    displayRegion = Rect(0f, 0f, displayArea.width.toFloat(), displayArea.height.toFloat())
                ),
                ))
                lastUsedScaleFactor = scaleFactor
            } catch (error: Throwable) {
                closeTileBitmaps(frames.flatMap { it.tiles })
                throw error
            }
        }.also { logger.info { "page ${pageId.pageNumber} completed full resize to $dstWidth x $dstHeight in $it" } }

    }

    // TODO support animations
    // does not handle animated images and assumes that there's only one frame
    private suspend fun doTile(
        image: KomeliaImage,
        displayRegion: Rect,
        displayScaleFactor: Double,
        scaleFactor: Double,
        displayArea: IntSize,
        tileSize: Int,
    ) {
        val timeSource = TimeSource.Monotonic
        val start = timeSource.markNow()

        ensureTileFallback(image, displayArea, scaleFactor)

        val oldTiles = frameData.value?.frames?.first()?.tiles ?: emptyList()
        val newTiles = mutableListOf<ReaderImageTile>()
        var addedNewTiles = false

        try {
            val specs = viewportTiles(ViewportTilePlan(IntSize(image.width, image.height), tileSize,
                displayScaleFactor, scaleFactor, displayRegion))
            for (spec in specs) {
                ensureForegroundCurrent()
                val existingTile = oldTiles.find { it.displayRegion == spec.display }
                if (existingTile != null && scaleFactor == lastUsedScaleFactor && existingTile.renderImage != null) {
                    newTiles.add(existingTile)
                    continue
                }
                val scaledTile = getImageRegion(image, spec.source, spec.pixels.width, spec.pixels.height)
                newTiles.add(ReaderImageTile(IntSize(scaledTile.width, scaledTile.height), spec.display, true, scaledTile.frames.first()))
                ensureForegroundCurrent()
                addedNewTiles = true
            }
        } catch (error: Throwable) {
            closeTileBitmaps(newTiles.filter { tile -> oldTiles.none { it === tile } })
            throw error
        }

        if (addedNewTiles || newTiles.size != oldTiles.size) {
            publishFrame(FrameData(
                frames = listOf(ImageFrame(newTiles, 0)),
                displaySize = displayArea,
                scaleFactor = scaleFactor,
                sourceImage = image,
                fallback = frameData.value?.fallback,
            ))

            val end = timeSource.markNow()
            logger.info { "page ${pageId.pageNumber} completed tiled resize in ${end - start};  ${newTiles.size} tiles" }
        }
        lastUsedScaleFactor = scaleFactor

    }

    // Generate once per processed static image, only when high-resolution tiling is needed.
    // Publish before waiting for tiles so every outgoing painter already covers the page.
    private suspend fun ensureTileFallback(image: KomeliaImage, displayArea: IntSize, scaleFactor: Double) {
        val previous = frameData.value
        val sameImage = previous?.sourceImage === image
        if (sameImage && previous?.fallback != null && previous.displaySize == displayArea) return
        val region = Rect(0f, 0f, displayArea.width.toFloat(), displayArea.height.toFloat())
        val existing = previous?.takeIf { sameImage }?.fallback
        val fallback = if (existing != null) existing.copy(displayRegion = region) else {
            val size = tileFallbackSize(IntSize(image.width, image.pageHeight))
            val resized = resizeImage(image, size.width, size.height)
            ReaderImageTile(
                size = IntSize(resized.width, resized.height),
                displayRegion = region,
                isVisible = true,
                renderImage = resized.frames.single(),
                isFallback = true,
            )
        }
        val frames = if (sameImage && previous?.displaySize == displayArea) previous.frames
        else listOf(ImageFrame(emptyList(), 0))
        publishFrame(FrameData(frames, displayArea, scaleFactor, image, fallback))
    }

    private fun publishFrame(next: FrameData) {
        val previousTiles = frameData.value?.allTiles().orEmpty()
        val retainedTiles = next.allTiles()
        frameData.value = next
        // Fallback geometry can change without replacing its pixels. Compare pixel ownership,
        // not tile wrappers, and never retire a preview reused by the next full/tiled frame.
        closeTileBitmaps(previousTiles.filter { old ->
            retainedTiles.none { it.renderImage === old.renderImage }
        })
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        imageAwaitScope.cancel()
        animationScope.cancel()
        // Cancellation cannot interrupt an already executing JNI call. Release only after
        // all processing children finish, including their cancellation/finally blocks.
        processingScope.coroutineContext.job.invokeOnCompletion {
            runCatching {
                try {
                    preparedFrames.clear()
                    releaseImages()
                    frameData.value?.allTiles()
                        ?.let { closeTileBitmaps(it) }
                } finally {
                    frameData.value = null
                    painter.value = null
                    imageSource.close()
                }
            }.onFailure { logger.catching(it) }
        }
        processingScope.cancel()
    }

    private fun releaseImages() {
        val processed = image.value
        val original = originalImage
        image.value = null
        originalImage = null
        try {
            processed?.close()
        } finally {
            if (original !== processed) original?.close()
        }
    }

    protected abstract fun closeTileBitmaps(tiles: List<ReaderImageTile>)
    protected abstract fun createTilePainter(
        tiles: List<ReaderImageTile>,
        displaySize: IntSize,
        scaleFactor: Double,
    ): TiledPainter

    protected abstract suspend fun resizeImage(
        image: KomeliaImage,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData

    protected abstract suspend fun getImageRegion(
        image: KomeliaImage,
        imageRegion: IntRect,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData

    data class ReaderImageTile(
        val size: IntSize,
        val displayRegion: Rect,
        val isVisible: Boolean,
        val renderImage: RenderImage?,
        val isFallback: Boolean = false,
    )

    data class ReaderImageData(
        val width: Int,
        val height: Int,
        val frames: List<RenderImage>,
        val delays: List<Long>?,
    )

    abstract class TiledPainter() : Painter() {
        abstract fun withSamplingMode(upsamplingMode: UpsamplingMode): TiledPainter
    }

    class FrameData(
        val frames: List<ImageFrame>,
        val displaySize: IntSize,
        val scaleFactor: Double,
        val sourceImage: KomeliaImage,
        val fallback: ReaderImageTile? = null,
    ) {
        fun allTiles(): List<ReaderImageTile> = listOfNotNull(fallback) + frames.flatMap { it.tiles }
    }

    data class ImageFrame(
        val tiles: List<ReaderImageTile>,
        val delay: Long
    )
}

internal fun tileFallbackSize(imageSize: IntSize): IntSize {
    require(imageSize.width > 0 && imageSize.height > 0)
    val scale = minOf(1.0, 768.0 / maxOf(imageSize.width, imageSize.height))
    return IntSize(
        (imageSize.width * scale).roundToInt().coerceAtLeast(1),
        (imageSize.height * scale).roundToInt().coerceAtLeast(1),
    )
}

internal fun tileVisibilityWindow(viewport: Rect): Rect = Rect(
    left = viewport.left - viewport.width / 4,
    top = viewport.top - viewport.height / 4,
    right = viewport.right + viewport.width / 4,
    bottom = viewport.bottom + viewport.height / 4,
)
