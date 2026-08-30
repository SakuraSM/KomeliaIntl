package snd.komelia.ui.settings.network

import snd.komelia.settings.ServerConnectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkSettingsStateTest {
    @Test
    fun blankLanAddressTakesPriorityOverResolverStatus() {
        assertEquals(
            LanConnectionUiState.NotConfigured,
            resolveLanConnectionUiState(
                lanServerUrl = " ",
                lanAutoSwitchEnabled = true,
                connectionStatus = ServerConnectionStatus.LanUnavailable("http://lan"),
            ),
        )
    }

    @Test
    fun disabledAutoSwitchIsExplainedBeforeShowingResolverStatus() {
        assertEquals(
            LanConnectionUiState.AutoSwitchDisabled,
            resolveLanConnectionUiState(
                lanServerUrl = "http://lan",
                lanAutoSwitchEnabled = false,
                connectionStatus = ServerConnectionStatus.Primary,
            ),
        )
    }

    @Test
    fun enabledLanConfigurationMapsEveryResolverState() {
        val url = "http://lan"
        assertEquals(
            LanConnectionUiState.UsingPrimary,
            resolveLanConnectionUiState(url, true, ServerConnectionStatus.Primary),
        )
        assertEquals(
            LanConnectionUiState.Checking,
            resolveLanConnectionUiState(url, true, ServerConnectionStatus.CheckingLan),
        )
        assertEquals(
            LanConnectionUiState.Connected(url),
            resolveLanConnectionUiState(url, true, ServerConnectionStatus.Lan(url)),
        )
        assertEquals(
            LanConnectionUiState.Unreachable,
            resolveLanConnectionUiState(url, true, ServerConnectionStatus.LanUnavailable(url)),
        )
    }
}
