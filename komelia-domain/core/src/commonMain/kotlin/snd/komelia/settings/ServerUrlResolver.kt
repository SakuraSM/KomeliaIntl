package snd.komelia.settings

import kotlinx.coroutines.flow.StateFlow

interface ServerUrlResolver {
    val effectiveServerUrl: StateFlow<String>
    val connectionStatus: StateFlow<ServerConnectionStatus>

    fun refresh()
}

sealed interface ServerConnectionStatus {
    data object Primary : ServerConnectionStatus
    data object CheckingLan : ServerConnectionStatus
    data class Lan(val url: String) : ServerConnectionStatus
    data class LanUnavailable(val url: String) : ServerConnectionStatus
}
