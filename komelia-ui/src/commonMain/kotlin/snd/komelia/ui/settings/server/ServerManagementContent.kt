package snd.komelia.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.dialog_cancel
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_empty_trash_confirm_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_cancel_all_tasks
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_cancel_all_tasks_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_deep_scan_action
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_deep_scan_all_libraries
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_deep_scan_all_libraries_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_empty_action
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_empty_all_trash
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_empty_all_trash_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_empty_library_trash
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_management
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_scan_action
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_scan_all_libraries
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_scan_all_libraries_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_shutdown
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_shutdown_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_shutdown_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_shutdown_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_server_stop_action
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.dialogs.ConfirmationDialog

@Composable
fun ServerManagementContent(
    onScanAllLibraries: (deep: Boolean) -> Unit,
    onEmptyTrash: () -> Unit,
    onCancelAllTasks: () -> Unit,
    onShutdown: () -> Unit
) {

    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(Res.string.settings_server_management), style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()
        Button(
            title = stringResource(Res.string.settings_server_scan_all_libraries),
            description = stringResource(Res.string.settings_server_scan_all_libraries_desc),
            buttonText = stringResource(Res.string.settings_server_scan_action),
            level = WarningLevel.NORMAL,
            onClick = { onScanAllLibraries(false) }
        )
        HorizontalDivider()
        Button(
            title = stringResource(Res.string.settings_server_deep_scan_all_libraries),
            description = stringResource(Res.string.settings_server_deep_scan_all_libraries_desc),
            buttonText = stringResource(Res.string.settings_server_deep_scan_action),
            level = WarningLevel.NORMAL,
            onClick = { onScanAllLibraries(true) }
        )
        HorizontalDivider()
        Button(
            title = stringResource(Res.string.settings_server_empty_all_trash),
            description = stringResource(Res.string.settings_server_empty_all_trash_desc),
            buttonText = stringResource(Res.string.settings_server_empty_action),
            level = WarningLevel.NORMAL,
            onClick = { showEmptyTrashDialog = true }
        )
        HorizontalDivider()
        Button(
            title = stringResource(Res.string.settings_server_cancel_all_tasks),
            description = stringResource(Res.string.settings_server_cancel_all_tasks_desc),
            buttonText = stringResource(Res.string.dialog_cancel),
            level = WarningLevel.WARNING,
            onClick = { onCancelAllTasks() }
        )
        HorizontalDivider()
        Button(
            title = stringResource(Res.string.settings_server_shutdown),
            description = stringResource(Res.string.settings_server_shutdown_desc),
            buttonText = stringResource(Res.string.settings_server_shutdown),
            level = WarningLevel.DANGER,
            onClick = { showShutdownDialog = true }
        )
        HorizontalDivider()

        if (showEmptyTrashDialog) {
            ConfirmationDialog(
                title = stringResource(Res.string.settings_server_empty_library_trash),
                body = stringResource(Res.string.library_empty_trash_confirm_body),
                buttonConfirm = stringResource(Res.string.settings_server_empty_action),
                buttonCancel = stringResource(Res.string.dialog_cancel),
                onDialogConfirm = onEmptyTrash,
                onDialogDismiss = { showEmptyTrashDialog = false }
            )
        }

        if (showShutdownDialog) {
            ConfirmationDialog(
                title = stringResource(Res.string.settings_server_shutdown_confirm),
                body = stringResource(Res.string.settings_server_shutdown_body),
                buttonConfirm = stringResource(Res.string.settings_server_stop_action),
                buttonCancel = stringResource(Res.string.dialog_cancel),
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,
                onDialogConfirm = onShutdown,
                onDialogDismiss = { showShutdownDialog = false }
            )
        }

    }
}

@Composable
private fun Button(
    title: String,
    description: String,
    buttonText: String,
    level: WarningLevel,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.labelLarge)
        }

        val colors = when (level) {
            WarningLevel.NORMAL -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )

            WarningLevel.WARNING -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

            WarningLevel.DANGER -> ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        FilledTonalButton(
            onClick = onClick,
            colors = colors,
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text(buttonText)
        }

    }
}


private enum class WarningLevel {
    NORMAL,
    WARNING,
    DANGER
}
