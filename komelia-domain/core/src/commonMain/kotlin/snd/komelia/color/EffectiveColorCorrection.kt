package snd.komelia.color

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komga.client.book.KomgaBookId

/** Explicit book policy wins. Missing legacy book settings inherit the disabled-by-default global value. */
@OptIn(ExperimentalCoroutinesApi::class)
fun BookColorCorrectionRepository.effectiveCorrection(
    bookId: KomgaBookId,
    defaultCorrection: Flow<DefaultColorCorrection?>,
): Flow<ColorCorrectionConfig?> = getMode(bookId).flatMapLatest { mode ->
    when (mode) {
        BookColorCorrectionMode.INHERIT -> defaultCorrection.map { it?.configuration }
        BookColorCorrectionMode.DISABLED -> flowOf(null)
        BookColorCorrectionMode.CUSTOM -> getCurrentType(bookId).flatMapLatest { type ->
            when (type) {
                ColorCorrectionType.COLOR_CURVES -> getCurve(bookId).map {
                    ColorCorrectionConfig(type, curves = it?.channels ?: ColorCurvePoints.DEFAULT)
                }
                ColorCorrectionType.COLOR_LEVELS -> getLevels(bookId).map {
                    ColorCorrectionConfig(type, levels = it?.channels ?: ColorLevelChannels.DEFAULT)
                }
                null -> flowOf(null)
            }
        }
    }
}.distinctUntilChanged()
