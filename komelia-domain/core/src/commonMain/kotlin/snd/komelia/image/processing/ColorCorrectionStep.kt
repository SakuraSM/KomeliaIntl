package snd.komelia.image.processing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import snd.komelia.color.ChannelsLookupTable
import snd.komelia.color.DefaultColorCorrection
import snd.komelia.color.effectiveCorrection
import snd.komelia.color.ColorCorrectionType.COLOR_CURVES
import snd.komelia.color.ColorCorrectionType.COLOR_LEVELS
import snd.komelia.color.ColorCurvePoints
import snd.komelia.color.ColorLevelChannels
import snd.komelia.color.Curve
import snd.komelia.color.Levels
import snd.komelia.color.RGBA8888LookupTable
import snd.komelia.color.identityMap
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komelia.image.ImageFormat
import snd.komelia.image.KomeliaImage
import snd.komelia.image.ReaderImage
import snd.komga.client.book.KomgaBookId

@OptIn(ExperimentalUnsignedTypes::class)
class ColorCorrectionStep(
    private val bookColorCorrectionRepository: BookColorCorrectionRepository,
    private val defaultCorrection: Flow<DefaultColorCorrection?> = flowOf(null),
) : ProcessingStep {
    private val bookId = MutableStateFlow<KomgaBookId?>(null)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelsLut = bookId.flatMapLatest { bookId ->
        bookId?.let { id -> bookColorCorrectionRepository.effectiveCorrection(id, defaultCorrection) }
            ?: flowOf(null)
    }.map { config ->
        when (config?.type) {
            COLOR_CURVES -> mapCurvePoints(config.curves)
            COLOR_LEVELS -> mapLevels(config.levels)
            null -> null
        }
    }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

    fun setBookFlow(idFlow: StateFlow<KomgaBookId?>) {
        idFlow.onEach { bookId.value = it }.launchIn(coroutineScope)
    }

    private suspend fun mapCurvePoints(points: ColorCurvePoints): ChannelsLookupTable {
        val colorLut = Curve(points.colorCurvePoints).lookupTable.first()
        val redLut = Curve(points.redCurvePoints).lookupTable.first()
        val greenLut = Curve(points.greenCurvePoints).lookupTable.first()
        val blueLut = Curve(points.blueCurvePoints).lookupTable.first()
        val rgbaLut = if (redLut == null && greenLut == null && blueLut == null) null
        else RGBA8888LookupTable(
            redLut ?: identityMap,
            greenLut ?: identityMap,
            blueLut ?: identityMap,
            identityMap
        )

        return ChannelsLookupTable(colorLut, rgbaLut)
    }

    private suspend fun mapLevels(levels: ColorLevelChannels): ChannelsLookupTable {
        val colorLut = Levels(levels.color).lookupTable.first()
        val redLut = Levels(levels.red).lookupTable.first()
        val greenLut = Levels(levels.green).lookupTable.first()
        val blueLut = Levels(levels.blue).lookupTable.first()
        val rgbaLut = if (redLut == null && greenLut == null && blueLut == null) null
        else RGBA8888LookupTable(
            redLut ?: identityMap,
            greenLut ?: identityMap,
            blueLut ?: identityMap,
            identityMap
        )

        return ChannelsLookupTable(colorLut, rgbaLut)
    }

    val isActive = channelsLut.map { it != null && (it.value != null || it.rgba != null) }

    override suspend fun process(pageId: ReaderImage.PageId, image: KomeliaImage): KomeliaImage? {
        val luts = channelsLut.first() ?: return null
        val colorMapped = luts.value?.let {
            when (image.type) {
                ImageFormat.GRAYSCALE_8 -> image.mapLookupTable(it.asByteArray())
                ImageFormat.RGBA_8888 -> image.mapLookupTable(RGBA8888LookupTable(it).interleaved.asByteArray())
                else -> null
            }
        }
        val rgbaLut = luts.rgba ?: return colorMapped
        val rgbaMapped = when (image.type) {
            ImageFormat.RGBA_8888 -> (colorMapped ?: image).mapLookupTable(rgbaLut.interleaved.asByteArray())
            else -> colorMapped
        }

        // RGB mapping is skipped for grayscale; in that case the caller still owns colorMapped.
        if (rgbaMapped !== colorMapped) colorMapped?.close()
        return rgbaMapped
    }

    override suspend fun addChangeListener(callback: () -> Unit) {
        channelsLut.drop(1)
            .onEach {
                callback()
            }
            .launchIn(coroutineScope)
    }
}
