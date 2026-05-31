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
import snd.komelia.settings.ServerConnectionStatus
import snd.komelia.ui.LocalStrings
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
    val legacyStrings = LocalStrings.current.legacy
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SwitchWithLabel(
            checked = lanAutoSwitchEnabled,
            onCheckedChange = onLanAutoSwitchEnabledChange,
            label = { Text(legacyStrings.forText("Automatically use LAN address")) }
        )

        OutlinedTextField(
            value = lanServerUrl,
            onValueChange = onLanServerUrlChange,
            label = { Text(legacyStrings.forText("LAN server address")) },
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
                enabled = lanServerUrlError == null && lanServerUrl.isNotBlank(),
            ) {
                Text(legacyStrings.forText("Check connection"))
            }
            Text(connectionStatus.localizedText(), style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            "${legacyStrings.forText("Current server address")}: $effectiveServerUrl",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ServerUrlValidationError.localizedMessage(): String {
    val loginStrings = LocalStrings.current.login
    return when (this) {
        ServerUrlValidationError.INVALID_URL -> loginStrings.invalidServerUrl
        ServerUrlValidationError.INVALID_PORT -> loginStrings.invalidServerPort
    }
}

@Composable
private fun ServerConnectionStatus.localizedText(): String {
    val legacyStrings = LocalStrings.current.legacy
    return when (this) {
        ServerConnectionStatus.Primary -> legacyStrings.forText("Using primary server address")
        ServerConnectionStatus.CheckingLan -> legacyStrings.forText("Checking LAN address")
        is ServerConnectionStatus.Lan -> "${legacyStrings.forText("Using LAN address")}: $url"
        is ServerConnectionStatus.LanUnavailable -> legacyStrings.forText("LAN address is unreachable")
    }
}
