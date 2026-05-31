package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import snd.komelia.settings.ServerConnectionStatus
import snd.komelia.settings.ServerUrlResolver

private const val LAN_PROBE_TIMEOUT_MILLIS = 3_000L
private const val KOMGA_USER_ME_PATH = "/api/v1/users/me"

private val serverUrlResolverLogger = KotlinLogging.logger("server-url-resolver")

class DefaultServerUrlResolver(
    private val primaryServerUrl: StateFlow<String>,
    private val lanServerUrl: StateFlow<String>,
    private val lanAutoSwitchEnabled: StateFlow<Boolean>,
    networkChangeEvents: Flow<Unit>,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
) : ServerUrlResolver {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var probeJob: Job? = null

    override val effectiveServerUrl = MutableStateFlow(primaryServerUrl.value)
    override val connectionStatus = MutableStateFlow<ServerConnectionStatus>(ServerConnectionStatus.Primary)

    init {
        combine(
            primaryServerUrl,
            lanServerUrl,
            lanAutoSwitchEnabled,
        ) { primaryUrl, lanUrl, isAutoSwitchEnabled ->
            LanSwitchConfig(
                primaryUrl = primaryUrl,
                lanUrl = lanUrl,
                isAutoSwitchEnabled = isAutoSwitchEnabled,
            )
        }
            .distinctUntilChanged()
            .onEach(::resolveServerUrl)
            .launchIn(scope)

        refreshRequests
            .onEach { resolveServerUrl(currentConfig()) }
            .launchIn(scope)

        networkChangeEvents
            .onEach { resolveServerUrl(currentConfig()) }
            .launchIn(scope)
    }

    override fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private fun resolveServerUrl(config: LanSwitchConfig) {
        probeJob?.cancel()

        if (!config.canProbeLan()) {
            usePrimaryServer(config.primaryUrl)
            return
        }

        connectionStatus.value = ServerConnectionStatus.CheckingLan
        probeJob = scope.launch {
            val isLanReachable = isKomgaReachable(config.lanUrl)
            if (isLanReachable) {
                effectiveServerUrl.value = config.lanUrl
                connectionStatus.value = ServerConnectionStatus.Lan(config.lanUrl)
            } else {
                effectiveServerUrl.value = config.primaryUrl
                connectionStatus.value = ServerConnectionStatus.LanUnavailable(config.lanUrl)
            }
        }
    }

    private fun usePrimaryServer(primaryUrl: String) {
        effectiveServerUrl.value = primaryUrl
        connectionStatus.value = ServerConnectionStatus.Primary
    }

    private suspend fun isKomgaReachable(serverUrl: String): Boolean {
        return runCatching {
            withTimeout(LAN_PROBE_TIMEOUT_MILLIS) {
                val response = httpClient.get(serverUrl.komgaProbeUrl())
                response.isReachableProbeResult()
            }
        }.getOrElse { error ->
            when (error) {
                is HttpRequestTimeoutException,
                is TimeoutCancellationException -> Unit

                else -> serverUrlResolverLogger.debug(error) { "LAN server probe failed for $serverUrl" }
            }
            false
        }
    }

    private fun currentConfig(): LanSwitchConfig {
        return LanSwitchConfig(
            primaryUrl = primaryServerUrl.value,
            lanUrl = lanServerUrl.value,
            isAutoSwitchEnabled = lanAutoSwitchEnabled.value,
        )
    }

    private fun String.komgaProbeUrl(): String {
        val baseUrl = trimEnd('/')
        return "$baseUrl$KOMGA_USER_ME_PATH"
    }

    private fun HttpResponse.isReachableProbeResult(): Boolean {
        return status.isSuccess() || status.value in 300..499
    }
}

private data class LanSwitchConfig(
    val primaryUrl: String,
    val lanUrl: String,
    val isAutoSwitchEnabled: Boolean,
) {
    fun canProbeLan(): Boolean {
        return primaryUrl.isNotBlank() && lanUrl.isNotBlank() && isAutoSwitchEnabled
    }
}
