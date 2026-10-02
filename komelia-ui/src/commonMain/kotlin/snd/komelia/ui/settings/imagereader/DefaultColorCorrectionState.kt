package snd.komelia.ui.settings.imagereader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.color.ColorCorrectionConfig
import snd.komelia.color.ColorCorrectionType
import snd.komelia.color.DefaultColorCorrection
import snd.komelia.color.repository.ColorCurvePresetRepository
import snd.komelia.color.repository.ColorLevelsPresetRepository
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.ui.LoadState

data class DefaultColorCorrectionDependencies(
    val settings: ImageReaderSettingsRepository,
    val curves: ColorCurvePresetRepository,
    val levels: ColorLevelsPresetRepository,
    val notifications: AppNotifications,
)

class DefaultColorCorrectionState(
    private val dependencies: DefaultColorCorrectionDependencies,
    private val scope: CoroutineScope,
) {
    val state = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val selected = MutableStateFlow<DefaultColorCorrection?>(null)
    val presets = MutableStateFlow<List<DefaultColorCorrection>>(emptyList())
    val isSaving = MutableStateFlow(false)

    suspend fun initialize() {
        state.value = LoadState.Loading
        dependencies.notifications.runCatchingToNotifications {
            selected.value = dependencies.settings.getDefaultColorCorrection().first()
            presets.value = dependencies.curves.getPresets().map {
                DefaultColorCorrection(it.name, ColorCorrectionConfig(ColorCorrectionType.COLOR_CURVES, curves = it.points))
            } + dependencies.levels.getPresets().map {
                DefaultColorCorrection(it.name, ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS, levels = it.channels))
            }
            state.value = LoadState.Success(Unit)
        }.onFailure { state.value = LoadState.Error(it) }
    }

    fun select(correction: DefaultColorCorrection?) {
        if (state.value !is LoadState.Success || !isSaving.compareAndSet(false, true)) return
        scope.launch {
            try {
                withContext(NonCancellable) {
                    dependencies.notifications.runCatchingToNotifications {
                        dependencies.settings.putDefaultColorCorrection(correction)
                        selected.value = correction
                    }
                }
            } finally { isSaving.value = false }
        }
    }
}
