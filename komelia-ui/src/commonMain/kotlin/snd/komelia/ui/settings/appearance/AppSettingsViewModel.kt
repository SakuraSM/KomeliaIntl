package snd.komelia.ui.settings.appearance

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AppLanguage
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.defaultCardWidth

class AppSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val writeScope: CoroutineScope? = null,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    private val cardWidthPreference = preference(defaultCardWidth, settingsRepository::putCardWidth)
    private val themePreference = preference(AppTheme.DARK, settingsRepository::putAppTheme)
    private val languagePreference = preference(AppLanguage.SYSTEM, settingsRepository::putAppLanguage)
    private val surfacePreference = preference(AppSurfaceStyle.CLASSIC, settingsRepository::putSurfaceStyle)
    private val transparencyPreference = preference(false, settingsRepository::putReduceTransparency)

    val cardWidth: Dp get() = cardWidthPreference.value.dp
    val currentTheme: AppTheme get() = themePreference.value
    val currentLanguage: AppLanguage get() = languagePreference.value
    val surfaceStyle: AppSurfaceStyle get() = surfacePreference.value
    val reduceTransparency: Boolean get() = transparencyPreference.value
    val hasSaveError: Boolean get() = preferences.any { it.hasSaveError }

    private val preferences get() = listOf(
        cardWidthPreference, themePreference, languagePreference, surfacePreference, transparencyPreference,
    )

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        mutableState.value = LoadState.Loading
        try {
            cardWidthPreference.restore(settingsRepository.getCardWidth().first())
            themePreference.restore(settingsRepository.getAppTheme().first())
            languagePreference.restore(settingsRepository.getAppLanguage().first())
            surfacePreference.restore(settingsRepository.getSurfaceStyle().first())
            transparencyPreference.restore(settingsRepository.getReduceTransparency().first())
            mutableState.value = LoadState.Success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = LoadState.Error(error)
        }
    }

    fun onCardWidthChange(cardWidth: Dp) = cardWidthPreference.update(cardWidth.value.toInt().coerceIn(150, 350))
    fun onAppThemeChange(theme: AppTheme) = themePreference.update(theme)
    fun onAppLanguageChange(language: AppLanguage) = languagePreference.update(language)
    fun onSurfaceStyleChange(style: AppSurfaceStyle) = surfacePreference.update(style)
    fun onReduceTransparencyChange(enabled: Boolean) = transparencyPreference.update(enabled)
    fun onResetCardWidth() = cardWidthPreference.update(defaultCardWidth)
    fun retryFailedChanges() = preferences.forEach { it.retry() }

    private fun <Value : Any> preference(initial: Value, save: suspend (Value) -> Unit): AppearancePreference<Value> =
        AppearancePreference(initial, { writeScope ?: screenModelScope }, save)
}
