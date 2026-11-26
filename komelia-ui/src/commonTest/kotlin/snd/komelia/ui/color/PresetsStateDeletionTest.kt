package snd.komelia.ui.color

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.AppNotifications
import snd.komelia.color.ColorCurvePoints
import snd.komelia.color.ColorCurvePreset
import snd.komelia.color.ColorLevelChannels
import snd.komelia.color.ColorLevelsPreset
import snd.komelia.color.repository.ColorCurvePresetRepository
import snd.komelia.color.repository.ColorLevelsPresetRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PresetsStateDeletionTest {
    @Test
    fun curveDeletionWaitsForPersistenceAndIgnoresRepeatedConfirmation() = runTest {
        val preset = ColorCurvePreset("Night", ColorCurvePoints.DEFAULT)
        val storedPresets = mutableListOf(preset)
        val allowDelete = CompletableDeferred<Unit>()
        var deletionCount = 0
        val repository = object : ColorCurvePresetRepository {
            override suspend fun getPresets() = storedPresets.toList()
            override suspend fun savePreset(preset: ColorCurvePreset) = error("Unexpected save")
            override suspend fun deletePreset(preset: ColorCurvePreset) {
                deletionCount++
                allowDelete.await()
                storedPresets.remove(preset)
            }
        }
        val state = CurvePresetsState(
            repository, MutableStateFlow(ColorCurvePoints.DEFAULT), {}, AppNotifications(), backgroundScope,
        )
        state.initialize()
        state.onPresetSelect(preset)

        state.onPresetDelete(preset)
        state.onPresetDelete(preset)
        runCurrent()
        assertEquals(1, deletionCount)
        assertTrue(state.isWritingPreset.value)
        assertEquals(listOf(preset), state.presets.value)
        assertEquals(preset, state.selectedPreset.value)

        allowDelete.complete(Unit)
        runCurrent()
        assertFalse(state.isWritingPreset.value)
        assertNull(state.selectedPreset.value)
        assertTrue(storedPresets.isEmpty())
        state.initialize()
        assertTrue(state.presets.value.isEmpty())
    }

    @Test
    fun failedLevelsDeletionPreservesSelectionAndCanBeRetried() = runTest {
        val preset = ColorLevelsPreset("Paper", ColorLevelChannels.DEFAULT)
        val storedPresets = mutableListOf(preset)
        var failDelete = true
        val repository = object : ColorLevelsPresetRepository {
            override suspend fun getPresets() = storedPresets.toList()
            override suspend fun savePreset(preset: ColorLevelsPreset) = error("Unexpected save")
            override suspend fun deletePreset(preset: ColorLevelsPreset) {
                check(!failDelete) { "Synthetic preset deletion failure" }
                storedPresets.remove(preset)
            }
        }
        val notifications = AppNotifications()
        val state = LevelsPresetsState(
            repository, MutableStateFlow(ColorLevelChannels.DEFAULT), {}, notifications, backgroundScope,
        )
        state.initialize()
        state.onPresetSelect(preset)
        state.onPresetDelete(preset)
        runCurrent()

        assertFalse(state.isWritingPreset.value)
        assertEquals(listOf(preset), state.presets.value)
        assertEquals(preset, state.selectedPreset.value)
        assertTrue(notifications.getNotifications().first().isNotEmpty())

        failDelete = false
        state.onPresetDelete(preset)
        runCurrent()
        assertTrue(storedPresets.isEmpty())
        assertTrue(state.presets.value.isEmpty())
    }
}
