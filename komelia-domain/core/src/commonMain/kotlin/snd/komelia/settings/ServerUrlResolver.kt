package snd.komelia.settings

import kotlinx.coroutines.flow.StateFlow

interface ServerUrlResolver {
    val effectiveServerUrl: StateFlow<String>
    val connectionStatus: StateFlow<ServerConnectionStatus>
    val resolution: StateFlow<ServerUrlResolution>

    fun isCurrent(route: ServerUrlResolution.Resolved): Boolean = resolution.value == route

    fun refresh()
}

sealed interface ServerUrlResolution {
    val generation: Long
    val primaryUrl: String

    data class Pending(override val generation: Long, override val primaryUrl: String) : ServerUrlResolution
    data class Resolved(
        override val generation: Long,
        override val primaryUrl: String,
        val effectiveUrl: String,
    ) : ServerUrlResolution
    data class Unconfigured(override val generation: Long) : ServerUrlResolution {
        override val primaryUrl: String = ""
    }
}

sealed interface ServerConnectionStatus {
    data object Primary : ServerConnectionStatus
    data object CheckingLan : ServerConnectionStatus
    data class Lan(val url: String) : ServerConnectionStatus
    data class LanUnavailable(val url: String) : ServerConnectionStatus
}
