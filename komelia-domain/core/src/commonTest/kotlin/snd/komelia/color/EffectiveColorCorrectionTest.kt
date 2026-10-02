package snd.komelia.color

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import snd.komelia.color.repository.BookColorCorrectionRepository
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EffectiveColorCorrectionTest {
    private val book = KomgaBookId("synthetic")
    private val default = DefaultColorCorrection("Saved gamma", ColorCorrectionConfig(
        ColorCorrectionType.COLOR_LEVELS,
        levels = ColorLevelChannels.DEFAULT.copy(color = ColorLevelsConfig.DEFAULT.copy(gamma = 2f)),
    ))

    @Test fun absentSettingsAreUnchangedUntilTheGlobalDefaultIsEnabled() = runTest {
        val repository = FakeRepository()
        val global = MutableStateFlow<DefaultColorCorrection?>(null)
        assertNull(repository.effectiveCorrection(book, global).first())
        global.value = default
        assertEquals(default.configuration, repository.effectiveCorrection(book, global).first())
    }

    @Test fun legacyCustomSettingsAndExplicitOffNeverFallBackToGlobal() = runTest {
        val repository = FakeRepository()
        val global = MutableStateFlow<DefaultColorCorrection?>(default)
        repository.setCurrentType(book, ColorCorrectionType.COLOR_CURVES)
        assertEquals(ColorCorrectionConfig(ColorCorrectionType.COLOR_CURVES), repository.effectiveCorrection(book, global).first())
        repository.setMode(book, BookColorCorrectionMode.DISABLED)
        assertNull(repository.effectiveCorrection(book, global).first())
    }

    @Test fun liveGlobalChangesApplyOnlyWhileInheritingAndRestoreOnReturn() = runTest {
        val repository = FakeRepository()
        val global = MutableStateFlow<DefaultColorCorrection?>(default)
        val values = mutableListOf<ColorCorrectionConfig?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.effectiveCorrection(book, global).collect { values.add(it) }
        }
        assertEquals(default.configuration, values.last())
        repository.setMode(book, BookColorCorrectionMode.DISABLED)
        val changed = default.copy(configuration = default.configuration.copy(
            levels = default.configuration.levels.copy(color = ColorLevelsConfig.DEFAULT.copy(gamma = 3f)),
        ))
        global.value = changed
        assertNull(values.last())
        repository.setMode(book, BookColorCorrectionMode.INHERIT)
        assertEquals(changed.configuration, values.last())
        global.value = null
        assertNull(values.last())
    }

    @Test fun customIdentityRemainsAnOverrideAndDoesNotEnableTheGlobalCorrection() = runTest {
        val repository = FakeRepository()
        repository.setCurrentType(book, ColorCorrectionType.COLOR_LEVELS)
        repository.saveLevels(BookColorLevels(book, ColorLevelChannels.DEFAULT))
        assertEquals(ColorLevelChannels.DEFAULT,
            repository.effectiveCorrection(book, MutableStateFlow(default)).first()?.levels)
    }

    private class FakeRepository : BookColorCorrectionRepository {
        private val mode = MutableStateFlow(BookColorCorrectionMode.INHERIT)
        private val type = MutableStateFlow<ColorCorrectionType?>(null)
        private val curves = MutableStateFlow<ColorCurveBookPoints?>(null)
        private val levels = MutableStateFlow<BookColorLevels?>(null)
        override fun getMode(bookId: KomgaBookId) = mode
        override suspend fun setMode(bookId: KomgaBookId, mode: BookColorCorrectionMode) { this.mode.value = mode }
        override fun getCurrentType(bookId: KomgaBookId) = type
        override suspend fun saveConfiguration(bookId: KomgaBookId, configuration: ColorCorrectionConfig) {
            setCurrentType(bookId, configuration.type)
            saveCurve(ColorCurveBookPoints(bookId, configuration.curves))
            saveLevels(BookColorLevels(bookId, configuration.levels))
        }
        override suspend fun setCurrentType(bookId: KomgaBookId, type: ColorCorrectionType) {
            this.type.value = type
            mode.value = BookColorCorrectionMode.CUSTOM
        }
        override suspend fun deleteSettings(bookId: KomgaBookId) { type.value = null; mode.value = BookColorCorrectionMode.INHERIT }
        override fun getCurve(bookId: KomgaBookId) = curves
        override suspend fun saveCurve(points: ColorCurveBookPoints) { curves.value = points }
        override suspend fun deleteCurve(bookId: KomgaBookId) { curves.value = null }
        override fun getLevels(bookId: KomgaBookId) = levels
        override suspend fun saveLevels(levels: BookColorLevels) { this.levels.value = levels }
        override suspend fun deleteLevels(bookId: KomgaBookId) { levels.value = null }
    }
}
