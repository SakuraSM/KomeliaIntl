package snd.komelia.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.utils.io.*
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_error_connection
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_error_invalid_credentials
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_error_server_unavailable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_error_timeout
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_error_unexpected_response
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.KomgaAuthenticationState
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaUserApi
import snd.komelia.offline.api.OfflineLibraryApi
import snd.komelia.offline.server.repository.OfflineMediaServerRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.OfflineLogEntry
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.offline.user.repository.OfflineUserRepository
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.SecretsRepository
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.ServerUrlValidationError
import snd.komelia.ui.common.validateServerUrl
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.settings.offline.OfflineOperationLogger

private val logger = KotlinLogging.logger { }

class LoginViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val secretsRepository: SecretsRepository,
    private val komgaUserApi: Flow<KomgaUserApi>,
    private val komgaLibraryApi: Flow<KomgaLibraryApi>,
    private val komgaAuthState: KomgaAuthenticationState,
    private val notifications: AppNotifications,
    private val platform: PlatformType,

    private val offlineUserRepository: OfflineUserRepository?,
    private val offlineServerRepository: OfflineMediaServerRepository?,
    private val offlineSettingsRepository: OfflineSettingsRepository?,
    private val offlineLibraryApi: OfflineLibraryApi?,
    logJournalRepository: LogJournalRepository?,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    private val operationLogger = logJournalRepository?.let { OfflineOperationLogger(it, screenModelScope) }

    var url by mutableStateOf("")
    var user by mutableStateOf("")
    var password by mutableStateOf("")
    var userLoginError by mutableStateOf<String?>(null)
    var serverUrlError by mutableStateOf<LoginServerUrlError?>(null)
    var autoLoginError by mutableStateOf<String?>(null)
    val offlineIsAvailable = MutableStateFlow(false)
    private val offlineUser = MutableStateFlow<OfflineUser?>(null)
    val canGoOfflineAsCurrentUser = offlineUser.map { it != null }

    fun initialize() {
        if (state.value !is Uninitialized) return

        screenModelScope.launch {
            url = settingsRepository.getServerUrl().first()
            user = settingsRepository.getCurrentUser().first()
            val offlineUsers = offlineUserRepository?.findAll() ?: emptyList()
            val offlineServer = offlineServerRepository?.findByUrl(url)

            offlineIsAvailable.value = offlineUsers.any { it.id != OfflineUser.ROOT }
            offlineUser.value = offlineServer?.let { server -> offlineUsers.firstOrNull { it.serverId == server.id } }
            val isOffline = offlineSettingsRepository?.getOfflineMode()?.first() ?: false

            when (platform) {
                MOBILE, DESKTOP -> {
                    if (isOffline || secretsRepository.getCookie(url) != null) {
                        tryAutologin()
                    } else {
                        mutableState.value = LoadState.Error(RuntimeException("Not logged in"))
                    }
                }

                WEB_KOMF -> tryAutologin()
            }
        }
    }

    fun retryAutoLogin() {
        screenModelScope.launch {
            mutableState.value = LoadState.Loading
            tryAutologin()
        }
    }

    fun cancel() {
        screenModelScope.coroutineContext.cancelChildren()
        mutableState.value = LoadState.Error(RuntimeException("Cancelled login attempt"))
        userLoginError = "Cancelled login attempt"
    }

    fun onUrlChange(newUrl: String) {
        url = newUrl
        serverUrlError = null
    }

    fun loginWithCredentials() {
        screenModelScope.launch {
            userLoginError = null
            serverUrlError = validateServerUrl(url)?.toLoginServerUrlError()
            if (serverUrlError != null) return@launch

            settingsRepository.putServerUrl(url)
            settingsRepository.putCurrentUser(user)
            tryUserLogin(user, password)
        }
    }

    fun offlineLogin() {
        notifications.runCatchingToNotifications(
            coroutineScope = screenModelScope,
            onFailure = { operationLogger?.record(OfflineLogEntry.Operation.LOGIN, it) },
        ) {
            val user = offlineUser.value ?: return@runCatchingToNotifications

            checkNotNull(offlineSettingsRepository).putOfflineMode(true)
            offlineSettingsRepository.putUserId(user.id)
            komgaAuthState.setStateValues(user.toKomgaUser(), checkNotNull(offlineLibraryApi).getLibraries())
            mutableState.value = LoadState.Success(Unit)
        }
    }

    private suspend fun tryAutologin() {
        try {
            tryLogin()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoTransformationFoundException) {
            val message = getString(Res.string.login_error_unexpected_response)
            autoLoginError = message
            notifications.add(AppNotification.Error(message))
            mutableState.value = LoadState.Error(e)
        } catch (e: ClientRequestException) {
            if (e.response.status == Unauthorized) {
                autoLoginError = null
            } else {
                autoLoginError = userFacingLoginError(e)
                notifications.add(AppNotification.Error(autoLoginError!!))
            }
            mutableState.value = LoadState.Error(e)
        } catch (e: Error) { // wasm fetch error
            val errorMessage = getString(Res.string.login_error_connection)
            mutableState.value = LoadState.Error(e)
            notifications.add(AppNotification.Error(errorMessage))
        } catch (e: Throwable) {
            logger.catching(e)
            val errorMessage = userFacingLoginError(e)
            autoLoginError = errorMessage
            mutableState.value = LoadState.Error(e)
            notifications.add(AppNotification.Error(errorMessage))
        }
    }

    private suspend fun tryUserLogin(username: String, password: String) {
        try {
            tryLogin(username, password)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoTransformationFoundException) {
            val message = getString(Res.string.login_error_unexpected_response)
            userLoginError = message
            mutableState.value = LoadState.Error(e)
        } catch (e: ClientRequestException) {
            userLoginError = if (e.response.status == Unauthorized) {
                getString(Res.string.login_error_invalid_credentials)
            } else {
                userFacingLoginError(e)
            }
            mutableState.value = LoadState.Error(e)
        } catch (e: Throwable) {
            logger.catching(e)
            userLoginError = userFacingLoginError(e)
            mutableState.value = LoadState.Error(e)
        }
    }

    private suspend fun tryLogin(
        username: String? = null,
        password: String? = null
    ) {
        val userApi = this.komgaUserApi.first()
        val libraryApi = this.komgaLibraryApi.first()
        val user =
            if (username != null && password != null) userApi.getMe(username, password, true)
            else userApi.getMe()

        val libraries = libraryApi.getLibraries()
        komgaAuthState.setStateValues(user, libraries)
        mutableState.value = LoadState.Success(Unit)
    }

    private suspend fun userFacingLoginError(exception: Throwable): String = when (exception) {
        is ServerResponseException -> getString(Res.string.login_error_server_unavailable)
        is HttpRequestTimeoutException -> getString(Res.string.login_error_timeout)
        is ResponseException -> getString(Res.string.login_error_unexpected_response)
        else -> getString(Res.string.login_error_connection)
    }
}

enum class LoginServerUrlError {
    INVALID_URL,
    INVALID_PORT
}

private fun ServerUrlValidationError.toLoginServerUrlError(): LoginServerUrlError {
    return when (this) {
        ServerUrlValidationError.INVALID_URL -> LoginServerUrlError.INVALID_URL
        ServerUrlValidationError.INVALID_PORT -> LoginServerUrlError.INVALID_PORT
    }
}

sealed class LoginResult {
    data object Loading : LoginResult()
    data object Error : LoginResult()
    data object Success : LoginResult()
}
