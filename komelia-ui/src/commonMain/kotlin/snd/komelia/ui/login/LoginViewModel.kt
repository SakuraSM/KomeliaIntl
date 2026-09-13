package snd.komelia.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
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
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.offline.server.repository.OfflineMediaServerRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.OfflineLogEntry
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.offline.user.repository.OfflineUserRepository
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.SecretsRepository
import snd.komelia.settings.ServerUrlResolver
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.common.ServerUrlValidationError
import snd.komelia.ui.common.validateServerUrl
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.settings.offline.OfflineOperationLogger

class LoginViewModel(
    private val settingsRepository: CommonSettingsRepository,
    private val secretsRepository: SecretsRepository,
    private val komgaUserApi: Flow<KomgaUserApi>,
    private val komgaLibraryApi: Flow<KomgaLibraryApi>,
    private val komgaAuthState: KomgaAuthenticationState,
    private val notifications: AppNotifications,
    private val platform: PlatformType,
    serverUrlResolver: ServerUrlResolver,

    private val offlineUserRepository: OfflineUserRepository?,
    private val offlineServerRepository: OfflineMediaServerRepository?,
    private val offlineSettingsRepository: OfflineSettingsRepository?,
    private val offlineLibraryApi: OfflineLibraryApi?,
    private val localLibraryManager: LocalLibraryManager?,
    logJournalRepository: LogJournalRepository?,
) : StateScreenModel<LoadState<Unit>>(Uninitialized) {

    private val operationLogger = logJournalRepository?.let { OfflineOperationLogger(it, screenModelScope) }
    private val loginCoordinator = OnlineLoginCoordinator(serverUrlResolver,
        loadUser = { credentials ->
            val api = komgaUserApi.first()
            if (credentials == null) api.getMe()
            else api.getMe(credentials.username, credentials.password, true)
        },
        loadLibraries = { komgaLibraryApi.first().getLibraries() },
    )
    private var loginJob: Job? = null
    private var isDisposed = false

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

        retryAutoLogin()
    }

    fun retryAutoLogin() {
        startLogin(isAutomatic = true) { prepareAutoLogin() }
    }

    fun cancel() {
        loginJob?.cancel()
        loginJob = null
        mutableState.value = LoadState.Error(RuntimeException("Cancelled login attempt"))
        userLoginError = null
        autoLoginError = null
    }

    override fun onDispose() {
        isDisposed = true
        loginJob?.cancel()
        loginJob = null
        super.onDispose()
    }

    fun onUrlChange(newUrl: String) {
        url = newUrl
        serverUrlError = null
    }

    fun loginWithCredentials() {
        serverUrlError = validateServerUrl(url)?.toLoginServerUrlError()
        if (serverUrlError != null) return
        val primaryUrl = url
        val credentials = LoginCredentials(user, password)
        startLogin(isAutomatic = false) {
            settingsRepository.putServerUrl(primaryUrl)
            currentCoroutineContext().ensureActive()
            settingsRepository.putCurrentUser(credentials.username)
            currentCoroutineContext().ensureActive()
            commitSession(loginCoordinator.login(primaryUrl, credentials))
        }
    }

    fun offlineLogin() {
        loginJob?.cancel()
        val user = offlineUser.value ?: return
        startLogin(isAutomatic = false) {
            checkNotNull(offlineSettingsRepository).putOfflineMode(true)
            currentCoroutineContext().ensureActive()
            offlineSettingsRepository.putUserId(user.id)
            commitSession(LoginSession(user.toKomgaUser(), checkNotNull(offlineLibraryApi).getLibraries(), null))
        }
    }

    fun localLibraryLogin() {
        loginJob?.cancel()
        startLogin(isAutomatic = false) {
            checkNotNull(localLibraryManager).prepareLocalMode()
            currentCoroutineContext().ensureActive()
            checkNotNull(offlineSettingsRepository).putOfflineMode(true)
            currentCoroutineContext().ensureActive()
            offlineSettingsRepository.putUserId(OfflineUser.ROOT)
            commitSession(LoginSession(
                OfflineUser.ROOT_USER.toKomgaUser(),
                checkNotNull(offlineLibraryApi).getLibraries(),
                null,
            ))
        }
    }

    val localLibraryIsAvailable: Boolean
        get() = localLibraryManager != null

    private fun startLogin(isAutomatic: Boolean, login: suspend () -> Unit) {
        if (isDisposed || loginJob?.isActive == true) return
        userLoginError = null
        autoLoginError = null
        val task = screenModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeoutOrNull(LOGIN_TOTAL_TIMEOUT_MILLIS) { login(); true }
                    ?: throw LoginTimeoutException()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                if (!isDisposed) showLoginFailure(error, isAutomatic)
            }
        }
        loginJob = task
        mutableState.value = LoadState.Loading
        task.start()
    }

    private suspend fun prepareAutoLogin() {
        val primaryUrl = settingsRepository.getServerUrl().first()
        val username = settingsRepository.getCurrentUser().first()
        val offlineUsers = offlineUserRepository?.findAll() ?: emptyList()
        val offlineServer = offlineServerRepository?.findByUrl(primaryUrl)
        val isOffline = offlineSettingsRepository?.getOfflineMode()?.first() ?: false
        val hasSession = isOffline || platform == WEB_KOMF ||
            (primaryUrl.isNotBlank() && secretsRepository.getCookie(primaryUrl) != null)
        currentCoroutineContext().ensureActive()
        url = primaryUrl
        user = username
        offlineIsAvailable.value = offlineUsers.any { it.id != OfflineUser.ROOT }
        offlineUser.value = offlineServer?.let { server -> offlineUsers.firstOrNull { it.serverId == server.id } }
        if (!hasSession) {
            mutableState.value = LoadState.Error(RuntimeException("Not logged in"))
            return
        }
        if (!isOffline) {
            serverUrlError = validateServerUrl(primaryUrl)?.toLoginServerUrlError()
            if (serverUrlError != null) {
                mutableState.value = LoadState.Error(IllegalArgumentException("Invalid server URL"))
                return
            }
        }
        commitSession(if (isOffline) loginCoordinator.loginOffline() else loginCoordinator.login(primaryUrl))
    }

    private suspend fun commitSession(session: LoginSession) {
        currentCoroutineContext().ensureActive()
        if (isDisposed) return
        loginCoordinator.requireCurrentRoute(session)
        komgaAuthState.setStateValues(session.user, session.libraries)
        mutableState.value = LoadState.Success(Unit)
    }

    private suspend fun showLoginFailure(error: Throwable, isAutomatic: Boolean) {
        val isUnauthorized = error is ClientRequestException && error.response.status == Unauthorized
        val message = if (isUnauthorized) getString(Res.string.login_error_invalid_credentials)
        else userFacingLoginError(error)
        currentCoroutineContext().ensureActive()
        operationLogger?.record(OfflineLogEntry.Operation.LOGIN, error)
        if (isAutomatic) {
            autoLoginError = if (isUnauthorized) null else message
            if (!isUnauthorized) notifications.add(AppNotification.Error(message))
        } else userLoginError = message
        mutableState.value = LoadState.Error(error)
    }

    private suspend fun userFacingLoginError(exception: Throwable): String = when (exception) {
        is ServerResponseException -> getString(Res.string.login_error_server_unavailable)
        is LoginTimeoutException, is HttpRequestTimeoutException -> getString(Res.string.login_error_timeout)
        is NoTransformationFoundException -> getString(Res.string.login_error_unexpected_response)
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
