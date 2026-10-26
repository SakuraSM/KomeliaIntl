package snd.komelia.ui.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import snd.komelia.ui.common.components.KomeliaChromeSurface
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_account_change_password
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_account_email
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_account_roles
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.dialogs.user.PasswordChangeDialog
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.user.KomgaUser

@Composable
fun AccountSettingsContent(user: KomgaUser) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        EmailDetails(user)
        RolesDetails(user)
        SettingsSection { PasswordDetails(user) }
    }
}

@Composable
private fun EmailDetails(user: KomgaUser) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        KomeliaChromeSurface(shape = RoundedCornerShape(50)) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
        Text(
            stringResource(Res.string.settings_account_email, user.email),
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RolesDetails(user: KomgaUser) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.settings_account_roles),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            user.roles.forEach { role ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(AppStrings.forUserRole(role)) }
                )
            }
        }
    }
}

@Composable
private fun PasswordDetails(user: KomgaUser) {

    var showPasswordDialog by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { showPasswordDialog = true },
        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
    ) {
        Text(stringResource(Res.string.settings_account_change_password))
    }
    if (showPasswordDialog) {
        PasswordChangeDialog(user = user, onDismiss = { showPasswordDialog = false })
    }
}
