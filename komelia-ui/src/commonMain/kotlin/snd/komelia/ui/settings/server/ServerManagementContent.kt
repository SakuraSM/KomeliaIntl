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
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.dialogs.ConfirmationDialog

@Composable
fun ServerManagementContent(
    onScanAllLibraries: (deep: Boolean) -> Unit,
    onEmptyTrash: () -> Unit,
    onCancelAllTasks: () -> Unit,
    onShutdown: () -> Unit
) {

    val strings = LocalStrings.current.legacy
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(strings.forText("Server Management"), style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()
        Button(
            title = strings.forText("Scan all libraries"),
            description = strings.forText("Check folders for new or removed books.\nUses the last modified time of parent folders"),
            buttonText = strings.forText("Scan"),
            level = WarningLevel.NORMAL,
            onClick = { onScanAllLibraries(false) }
        )
        HorizontalDivider()
        Button(
            title = strings.forText("Deep scan all libraries"),
            description = strings.forText("Force the scanner to compare all scanned books with the ones stored in the database"),
            buttonText = strings.forText("Deep Scan"),
            level = WarningLevel.NORMAL,
            onClick = { onScanAllLibraries(true) }
        )
        HorizontalDivider()
        Button(
            title = strings.forText("Empty trash for all libraries"),
            description = strings.forText("Delete items marked as unavailable"),
            buttonText = strings.forText("Empty"),
            level = WarningLevel.NORMAL,
            onClick = { showEmptyTrashDialog = true }
        )
        HorizontalDivider()
        Button(
            title = strings.forText("Cancel all tasks"),
            description = strings.forText("Cancel all currently running tasks"),
            buttonText = strings.forText("Cancel"),
            level = WarningLevel.WARNING,
            onClick = { onCancelAllTasks() }
        )
        HorizontalDivider()
        Button(
            title = strings.forText("Shutdown"),
            description = strings.forText("Stop Komga application process"),
            buttonText = strings.forText("Shutdown"),
            level = WarningLevel.DANGER,
            onClick = { showShutdownDialog = true }
        )
        HorizontalDivider()

        if (showEmptyTrashDialog) {
            ConfirmationDialog(
                title = strings.forText("Empty trash for library"),
                body = strings.forText("Empty trash for all libraries confirmation"),
                buttonConfirm = strings.forText("Empty"),
                buttonCancel = strings.forText("Cancel"),
                onDialogConfirm = onEmptyTrash,
                onDialogDismiss = { showEmptyTrashDialog = false }
            )
        }

        if (showShutdownDialog) {
            ConfirmationDialog(
                title = strings.forText("Shut down server"),
                body = strings.forText("Are you sure you want to stop Komga?"),
                buttonConfirm = strings.forText("Stop"),
                buttonCancel = strings.forText("Cancel"),
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
