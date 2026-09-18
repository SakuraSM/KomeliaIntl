package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.settings.ServerConnectionStatus
import snd.komelia.settings.ServerUrlResolution
import snd.komelia.settings.ServerUrlResolver

private const val LAN_PROBE_TIMEOUT_MILLIS = 3_000L
private const val KOMGA_USER_ME_PATH = "/api/v1/users/me"

private val serverUrlResolverLogger = KotlinLogging.logger("server-url-resolver")

class DefaultServerUrlResolver(
    private val primaryServerUrl: StateFlow<String>,
    private val lanServerUrl: StateFlow<String>,
    private val lanAutoSwitchEnabled: StateFlow<Boolean>,
    networkChangeEvents: Flow<Unit>,
    private val probe: LanServerProbe,
    private val scope: CoroutineScope,
) : ServerUrlResolver {
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var probeJob: Job? = null
    private val resolutionMutex = Mutex()
    private var generation = 0L
    private val resolvedConfig = MutableStateFlow<LanSwitchConfig?>(null)

    constructor(
        primaryServerUrl: StateFlow<String>, lanServerUrl: StateFlow<String>,
        lanAutoSwitchEnabled: StateFlow<Boolean>, networkChangeEvents: Flow<Unit>,
        httpClient: HttpClient, scope: CoroutineScope,
    ) : this(primaryServerUrl, lanServerUrl, lanAutoSwitchEnabled, networkChangeEvents,
        LanServerProbe { url -> httpClient.get(url.trimEnd('/') + KOMGA_USER_ME_PATH).isReachableProbeResult() }, scope)

    override val effectiveServerUrl = MutableStateFlow(primaryServerUrl.value)
    override val connectionStatus = MutableStateFlow<ServerConnectionStatus>(ServerConnectionStatus.Primary)
    override val resolution = MutableStateFlow<ServerUrlResolution>(
        if (primaryServerUrl.value.isBlank()) ServerUrlResolution.Unconfigured(generation)
        else ServerUrlResolution.Pending(generation, primaryServerUrl.value)
    )

    init {
        combine(primaryServerUrl, lanServerUrl, lanAutoSwitchEnabled, ::LanSwitchConfig)
            .distinctUntilChanged()
            .onEach(::resolveServerUrl)
            .launchIn(scope)

        refreshRequests.onEach { resolveServerUrl(currentConfig()) }.launchIn(scope)
        networkChangeEvents.onEach { resolveServerUrl(currentConfig()) }.launchIn(scope)
    }

    override fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    override fun isCurrent(route: ServerUrlResolution.Resolved): Boolean =
        resolution.value == route && resolvedConfig.value == currentConfig()

    private suspend fun resolveServerUrl(config: LanSwitchConfig) = resolutionMutex.withLock {
        if (config != currentConfig()) return@withLock
        val currentGeneration = ++generation
        probeJob?.cancel()
        resolution.value = ServerUrlResolution.Pending(currentGeneration, config.primaryUrl)
        if (!config.canProbeLan()) {
            usePrimaryServer(config.primaryUrl)
            resolvedConfig.value = config
            resolution.value = if (config.primaryUrl.isBlank()) ServerUrlResolution.Unconfigured(currentGeneration)
            else ServerUrlResolution.Resolved(currentGeneration, config.primaryUrl, config.primaryUrl)
            return@withLock
        }

        connectionStatus.value = ServerConnectionStatus.CheckingLan
        probeJob = scope.launch {
            val isReachable = isKomgaReachable(config.lanUrl)
            currentCoroutineContext().ensureActive()
            resolutionMutex.withLock {
                if (generation != currentGeneration || config != currentConfig()) return@withLock
                effectiveServerUrl.value = if (isReachable) config.lanUrl else config.primaryUrl
                connectionStatus.value = if (isReachable) ServerConnectionStatus.Lan(config.lanUrl)
                else ServerConnectionStatus.LanUnavailable(config.lanUrl)
                resolvedConfig.value = config
                resolution.value = ServerUrlResolution.Resolved(currentGeneration, config.primaryUrl, effectiveServerUrl.value)
            }
        }
    }

    private fun usePrimaryServer(primaryUrl: String) {
        effectiveServerUrl.value = primaryUrl
        connectionStatus.value = ServerConnectionStatus.Primary
    }

    private suspend fun isKomgaReachable(serverUrl: String): Boolean = try {
        withTimeoutOrNull(LAN_PROBE_TIMEOUT_MILLIS) { probe.isReachable(serverUrl) } ?: false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        serverUrlResolverLogger.debug { "LAN server probe failed: ${error::class.simpleName}" }
        false
    }

    private fun currentConfig() = LanSwitchConfig(
        primaryServerUrl.value,
        lanServerUrl.value,
        lanAutoSwitchEnabled.value,
    )

}

fun interface LanServerProbe {
    suspend fun isReachable(serverUrl: String): Boolean
}

private fun HttpResponse.isReachableProbeResult(): Boolean = status.isSuccess() || status.value in 300..499

private data class LanSwitchConfig(
    val primaryUrl: String,
    val lanUrl: String,
    val isAutoSwitchEnabled: Boolean,
) {
    fun canProbeLan() = primaryUrl.isNotBlank() && lanUrl.isNotBlank() && isAutoSwitchEnabled
}
