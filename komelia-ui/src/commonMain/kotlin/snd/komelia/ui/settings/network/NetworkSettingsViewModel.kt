package snd.komelia.ui.settings.network

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.ServerUrlResolver
import snd.komelia.ui.common.ServerUrlValidationError
import snd.komelia.ui.common.validateServerUrl

class NetworkSettingsViewModel(
    private val settingsRepository: CommonSettingsRepository,
    val serverUrlResolver: ServerUrlResolver,
) : ScreenModel {
    var lanServerUrl by mutableStateOf("")
        private set
    var lanAutoSwitchEnabled by mutableStateOf(false)
        private set
    var lanServerUrlError by mutableStateOf<ServerUrlValidationError?>(null)
        private set

    fun initialize() {
        screenModelScope.launch {
            lanServerUrl = settingsRepository.getLanServerUrl().first()
            lanAutoSwitchEnabled = settingsRepository.getLanAutoSwitchEnabled().first()
            lanServerUrlError = validateOptionalLanUrl(lanServerUrl)
        }
    }

    fun onLanServerUrlChange(url: String) {
        lanServerUrl = url
        lanServerUrlError = validateOptionalLanUrl(url)
        if (lanServerUrlError == null) {
            screenModelScope.launch {
                settingsRepository.putLanServerUrl(url.trim())
                serverUrlResolver.refresh()
            }
        }
    }

    fun onLanAutoSwitchEnabledChange(enabled: Boolean) {
        lanAutoSwitchEnabled = enabled
        screenModelScope.launch {
            settingsRepository.putLanAutoSwitchEnabled(enabled)
            serverUrlResolver.refresh()
        }
    }

    fun checkLanConnection() {
        lanServerUrlError = validateOptionalLanUrl(lanServerUrl)
        if (lanServerUrlError != null) return

        screenModelScope.launch {
            settingsRepository.putLanServerUrl(lanServerUrl.trim())
            serverUrlResolver.refresh()
        }
    }

    private fun validateOptionalLanUrl(url: String): ServerUrlValidationError? {
        if (url.isBlank()) return null
        return validateServerUrl(url.trim())
    }
}
