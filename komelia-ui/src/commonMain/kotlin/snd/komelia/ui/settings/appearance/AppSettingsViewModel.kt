package snd.komelia.ui.settings.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.model.AppLanguage
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.defaultCardWidth

class AppSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val writeScope: CoroutineScope? = null,
) : StateScreenModel<LoadState<Unit>>(LoadState.Uninitialized) {
    var cardWidth by mutableStateOf(defaultCardWidth.dp)
    var currentTheme by mutableStateOf(AppTheme.DARK)
    var currentLanguage by mutableStateOf(AppLanguage.SYSTEM)
    private val cardWidthSaveMutex = Mutex()
    private var savedCardWidth: Int? = null

    suspend fun initialize() {
        if (state.value !is LoadState.Uninitialized) return
        mutableState.value = LoadState.Loading
        cardWidth = settingsRepository.getCardWidth().map { it.dp }.first()
        savedCardWidth = cardWidth.value.toInt()
        currentTheme = settingsRepository.getAppTheme().first()
        currentLanguage = settingsRepository.getAppLanguage().first()
        mutableState.value = LoadState.Success(Unit)
    }

    fun onCardWidthChange(cardWidth: Dp) {
        this.cardWidth = cardWidth.value.coerceIn(150f, 350f).dp
        (writeScope ?: screenModelScope).launch {
            cardWidthSaveMutex.withLock {
                // Coalesce drag events, serialize saves, and finish the last selection even
                // if navigating away cancels this screen's scope during a database write.
                withContext(NonCancellable) {
                    while (savedCardWidth != this@AppSettingsViewModel.cardWidth.value.toInt()) {
                        val latest = this@AppSettingsViewModel.cardWidth.value.toInt()
                        settingsRepository.putCardWidth(latest)
                        savedCardWidth = latest
                    }
                }
            }
        }
    }

    fun onAppThemeChange(theme: AppTheme) {
        this.currentTheme = theme
        screenModelScope.launch { settingsRepository.putAppTheme(theme) }
    }

    fun onAppLanguageChange(language: AppLanguage) {
        this.currentLanguage = language
        screenModelScope.launch { settingsRepository.putAppLanguage(language) }
    }

}
