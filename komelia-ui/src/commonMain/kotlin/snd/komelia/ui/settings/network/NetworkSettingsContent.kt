package snd.komelia.ui.settings.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_auto_lan
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_check
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_checking_lan
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_current_server
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_invalid_port
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_invalid_url
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_lan_address
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_lan_auto_disabled
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_lan_not_configured
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_lan_unreachable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_using_lan
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_using_primary
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.ServerConnectionStatus
import snd.komelia.ui.common.ServerUrlValidationError
import snd.komelia.ui.common.components.SwitchWithLabel

@Composable
fun NetworkSettingsContent(
    lanServerUrl: String,
    lanServerUrlError: ServerUrlValidationError?,
    lanAutoSwitchEnabled: Boolean,
    connectionStatus: ServerConnectionStatus,
    effectiveServerUrl: String,
    onLanServerUrlChange: (String) -> Unit,
    onLanAutoSwitchEnabledChange: (Boolean) -> Unit,
    onCheckLanConnection: () -> Unit,
) {
    val lanConnectionUiState = resolveLanConnectionUiState(
        lanServerUrl = lanServerUrl,
        lanAutoSwitchEnabled = lanAutoSwitchEnabled,
        connectionStatus = connectionStatus,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SwitchWithLabel(
            checked = lanAutoSwitchEnabled,
            onCheckedChange = onLanAutoSwitchEnabledChange,
            label = { Text(stringResource(Res.string.settings_network_auto_lan)) },
            supportingText = {
                when (lanConnectionUiState) {
                    LanConnectionUiState.NotConfigured -> {
                        Text(stringResource(Res.string.settings_network_lan_not_configured))
                    }
                    LanConnectionUiState.AutoSwitchDisabled -> {
                        Text(stringResource(Res.string.settings_network_lan_auto_disabled))
                    }
                    else -> Unit
                }
            },
        )

        OutlinedTextField(
            value = lanServerUrl,
            onValueChange = onLanServerUrlChange,
            label = { Text(stringResource(Res.string.settings_network_lan_address)) },
            placeholder = { Text("http://192.168.1.10:25600") },
            isError = lanServerUrlError != null,
            supportingText = lanServerUrlError?.let { error ->
                { Text(error.localizedMessage()) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().widthIn(min = 250.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onCheckLanConnection,
                enabled = lanAutoSwitchEnabled && lanServerUrlError == null && lanServerUrl.isNotBlank(),
            ) {
                Text(stringResource(Res.string.settings_network_check))
            }
            if (
                lanConnectionUiState != LanConnectionUiState.NotConfigured &&
                lanConnectionUiState != LanConnectionUiState.AutoSwitchDisabled
            ) {
                Text(
                    lanConnectionUiState.localizedText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lanConnectionUiState is LanConnectionUiState.Unreachable) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Text(
            stringResource(Res.string.settings_network_current_server, effectiveServerUrl),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ServerUrlValidationError.localizedMessage(): String {
    return when (this) {
        ServerUrlValidationError.INVALID_URL -> stringResource(Res.string.settings_network_invalid_url)
        ServerUrlValidationError.INVALID_PORT -> stringResource(Res.string.settings_network_invalid_port)
    }
}

@Composable
private fun LanConnectionUiState.localizedText(): String {
    return when (this) {
        LanConnectionUiState.NotConfigured -> stringResource(Res.string.settings_network_lan_not_configured)
        LanConnectionUiState.AutoSwitchDisabled -> stringResource(Res.string.settings_network_lan_auto_disabled)
        LanConnectionUiState.UsingPrimary -> stringResource(Res.string.settings_network_using_primary)
        LanConnectionUiState.Checking -> stringResource(Res.string.settings_network_checking_lan)
        is LanConnectionUiState.Connected -> stringResource(Res.string.settings_network_using_lan, url)
        LanConnectionUiState.Unreachable -> stringResource(Res.string.settings_network_lan_unreachable)
    }
}

internal sealed interface LanConnectionUiState {
    data object NotConfigured : LanConnectionUiState
    data object AutoSwitchDisabled : LanConnectionUiState
    data object UsingPrimary : LanConnectionUiState
    data object Checking : LanConnectionUiState
    data class Connected(val url: String) : LanConnectionUiState
    data object Unreachable : LanConnectionUiState
}

internal fun resolveLanConnectionUiState(
    lanServerUrl: String,
    lanAutoSwitchEnabled: Boolean,
    connectionStatus: ServerConnectionStatus,
): LanConnectionUiState {
    if (lanServerUrl.isBlank()) return LanConnectionUiState.NotConfigured
    if (!lanAutoSwitchEnabled) return LanConnectionUiState.AutoSwitchDisabled

    return when (connectionStatus) {
        ServerConnectionStatus.Primary -> LanConnectionUiState.UsingPrimary
        ServerConnectionStatus.CheckingLan -> LanConnectionUiState.Checking
        is ServerConnectionStatus.Lan -> LanConnectionUiState.Connected(connectionStatus.url)
        is ServerConnectionStatus.LanUnavailable -> LanConnectionUiState.Unreachable
    }
}
