package snd.komelia.ui.color

import androidx.compose.ui.unit.IntSize
import cafe.adriel.voyager.core.model.StateScreenModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import snd.komelia.AppNotifications
import snd.komelia.color.BookColorCorrectionMode
import snd.komelia.color.ColorCorrectionConfig
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.color.ChannelsLut
import snd.komelia.color.ColorCorrectionType
import snd.komelia.color.ColorCorrectionType.COLOR_CURVES
import snd.komelia.color.ColorCorrectionType.COLOR_LEVELS
import snd.komelia.color.ColorCurvePoints
import snd.komelia.color.ColorLevelChannels
import snd.komelia.color.Histogram
import snd.komelia.color.RGBA8888LookupTable
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.color.repository.ColorCurvePresetRepository
import snd.komelia.color.repository.ColorLevelsPresetRepository
import snd.komelia.image.BookImageLoader
import snd.komelia.image.ImageFormat
import snd.komelia.image.ImageResult
import snd.komelia.image.KomeliaImage
import snd.komelia.image.toImageBitmap
import snd.komelia.ui.LoadState
import snd.komga.client.book.KomgaBookId
import kotlin.math.max

class ColorCorrectionViewModel(
    private val bookColorCorrectionRepository: BookColorCorrectionRepository,
    curvePresetRepository: ColorCurvePresetRepository,
    levelsPresetRepository: ColorLevelsPresetRepository,
    private val imageLoader: BookImageLoader,
    private val appNotifications: AppNotifications,
    private val bookId: KomgaBookId,
    private val pageNumber: Int,
    private val settingsRepository: ImageReaderSettingsRepository,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    private val originalImage = MutableStateFlow<KomeliaImage?>(null)
    private val histogram = MutableStateFlow(Histogram(ByteArray(0)))
    private val imageMaxSize = MutableStateFlow<IntSize?>(null)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val correctionType = MutableStateFlow(COLOR_CURVES)
    val mode = MutableStateFlow(BookColorCorrectionMode.INHERIT)
    val isSaving = MutableStateFlow(false)
    private var defaultConfiguration: ColorCorrectionConfig? = null
    private var customConfiguration = ColorCorrectionConfig(COLOR_CURVES)

    val curvesState = CurvesState(
        appNotifications = appNotifications,
        curvePresetRepository = curvePresetRepository,
        histogram = histogram,
        coroutineScope = coroutineScope,
        bookCurvesRepository = bookColorCorrectionRepository,
        bookId = bookId,
    )

    val levelsState = LevelsState(
        appNotifications = appNotifications,
        levelsPresetRepository = levelsPresetRepository,
        histogram = histogram,
        coroutineScope = coroutineScope,
        bookLevelsRepository = bookColorCorrectionRepository,
        bookId = bookId,
    )

    private val curveLut = combine(
        curvesState.colorCurve.lookupTable,
        curvesState.rgbaLut,
    ) { color, rgba -> ChannelsLut(color, rgba) }

    private val levelsLut = combine(
        levelsState.colorLevels.lookupTable,
        levelsState.rgbaLut,
    ) { color, rgba -> ChannelsLut(color, rgba) }

    private val currentLut = combine(correctionType, curveLut, levelsLut) { type, curves, levels ->
        when (type) {
            COLOR_CURVES -> curves
            COLOR_LEVELS -> levels
        }
    }

    val displayImage = combine(
        originalImage.filterNotNull(),
        imageMaxSize.filterNotNull(),
        currentLut
    ) { image, targetSize, channelsLut -> Triple(image, targetSize, channelsLut) }
        .conflate()
        .mapNotNull { (image, targetSize, channelsLut) ->
            val throttleDelay = coroutineScope { async { delay(100) } }
            val processed = processDisplayImage(image, targetSize, channelsLut)
            val bitmap = try { processed.toImageBitmap() }
            finally { if (processed !== image) processed.close() }

            throttleDelay.await()
            bitmap
        }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

    private suspend fun processDisplayImage(
        image: KomeliaImage,
        targetSize: IntSize,
        channelsLut: ChannelsLut
    ): KomeliaImage {
        suspend fun mapColor(image: KomeliaImage, colorLut: UByteArray?): KomeliaImage? {
            return if (colorLut == null) null
            else when (image.type) {
                ImageFormat.GRAYSCALE_8 -> image.mapLookupTable(colorLut.asByteArray())
                ImageFormat.RGBA_8888 -> image.mapLookupTable(RGBA8888LookupTable(colorLut).interleaved.asByteArray())
                else -> null
            }
        }

        suspend fun mapRGBA(image: KomeliaImage, rgbLut: RGBA8888LookupTable?): KomeliaImage? {
            return if (rgbLut == null) null
            else when (image.type) {
                ImageFormat.RGBA_8888 -> image.mapLookupTable(rgbLut.interleaved.asByteArray())
                else -> null
            }
        }

        suspend fun scale(image: KomeliaImage, targetSize: IntSize): KomeliaImage? {
            val heightFactor = image.height.toDouble() / targetSize.height
            val widthFactor = image.width.toDouble() / targetSize.width
            val scaleFactor = max(heightFactor, widthFactor)
            return if (scaleFactor > 1.0) {
                image.shrink(scaleFactor)
            } else null
        }

        val colorLut = channelsLut.colorLut
        val rgbLut = channelsLut.rgbaLut
        val colorMapped = mapColor(image, colorLut)
        val rgbaMapped = mapRGBA(colorMapped ?: image, rgbLut)
        val resized = scale(rgbaMapped ?: colorMapped ?: image, targetSize)

        when {
            resized != null -> {
                colorMapped?.close()
                rgbaMapped?.close()
                return resized
            }

            rgbaMapped != null -> {
                colorMapped?.close()
                return rgbaMapped
            }

            colorMapped != null -> return colorMapped
            else -> return image
        }

    }

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        appNotifications.runCatchingToNotifications {
            mutableState.value = LoadState.Loading
            when (val result = imageLoader.loadImage(bookId, pageNumber)) {
                is ImageResult.Error -> {
                    mutableState.value = LoadState.Error(result.throwable)
                    return
                }

                is ImageResult.Success -> {
                    val image = result.image
                    originalImage.value = image
                    val histogramImage = image.makeHistogram()
                    histogram.value = Histogram(histogramImage.getBytes())
                    histogramImage.close()
                }
            }

            curvesState.initialize()
            levelsState.initialize()

            val storedType = bookColorCorrectionRepository.getCurrentType(bookId).first()
            correctionType.value = storedType ?: COLOR_CURVES
            defaultConfiguration = settingsRepository.getDefaultColorCorrection().first()?.configuration
            customConfiguration = if (storedType == null) defaultConfiguration ?: currentConfiguration() else currentConfiguration()
            mode.value = bookColorCorrectionRepository.getMode(bookId).first()
            applyModePreview()
            mutableState.value = LoadState.Success(Unit)
        }.onFailure {
            mutableState.value = LoadState.Error(it)
        }
    }

    fun onImageMaxSizeChange(size: IntSize) {
        imageMaxSize.value = size
    }

    fun onCurveTypeChange(type: ColorCorrectionType) {
        correctionType.value = type
    }

    fun onModeChange(next: BookColorCorrectionMode) {
        if (isSaving.value || next == mode.value) return
        if (mode.value == BookColorCorrectionMode.CUSTOM) customConfiguration = currentConfiguration()
        mode.value = next
        applyModePreview()
    }

    private fun applyModePreview() {
        val configuration = when (mode.value) {
            BookColorCorrectionMode.INHERIT -> defaultConfiguration ?: ColorCorrectionConfig(COLOR_CURVES)
            BookColorCorrectionMode.CUSTOM -> customConfiguration
            BookColorCorrectionMode.DISABLED -> ColorCorrectionConfig(COLOR_CURVES)
        }
        correctionType.value = configuration.type
        curvesState.setChannels(configuration.curves)
        levelsState.setChannels(configuration.levels)
    }

    private fun currentConfiguration(): ColorCorrectionConfig = ColorCorrectionConfig(
        type = correctionType.value,
        curves = ColorCurvePoints(curvesState.colorCurve.points.value, curvesState.redCurve.points.value,
            curvesState.greenCurve.points.value, curvesState.blueCurve.points.value),
        levels = ColorLevelChannels(levelsState.colorLevels.levelsConfig.value, levelsState.redLevels.levelsConfig.value,
            levelsState.greenLevels.levelsConfig.value, levelsState.blueLevels.levelsConfig.value),
    )

    suspend fun onSave(): Boolean {
        if (state.value !is LoadState.Success || !isSaving.compareAndSet(false, true)) return false
        try {
            return appNotifications.runCatchingToNotifications {
                if (mode.value == BookColorCorrectionMode.CUSTOM) {
                    bookColorCorrectionRepository.saveConfiguration(bookId, currentConfiguration())
                } else {
                    bookColorCorrectionRepository.setMode(bookId, mode.value)
                }
            }.isSuccess
        } finally { isSaving.value = false }
    }

    override fun onDispose() {
        // Native preview work may suspend on another dispatcher; release only once it has stopped.
        coroutineScope.coroutineContext.job.invokeOnCompletion {
            val image = originalImage.value
            originalImage.value = null
            image?.close()
        }
        coroutineScope.cancel()
    }
}
