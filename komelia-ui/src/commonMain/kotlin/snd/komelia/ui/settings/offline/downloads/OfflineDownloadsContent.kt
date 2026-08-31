package snd.komelia.ui.settings.offline.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_cancel
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_canceled
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_completed
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_failed
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_pause
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_paused
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_queued
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_remove
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_remove_files
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_remove_files_confirm_body
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_remove_files_confirm_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_resume
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_retry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_running
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_speed
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_tasks_overall_progress
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_tasks_summary
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_tasks_empty
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.download_task_view_logs
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_storage_location
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_storage_location_change
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_storage_location_reset
import io.github.vinceglb.filekit.PlatformFile
import org.jetbrains.compose.resources.stringResource
import snd.komelia.formatDecimal
import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.StoragePermissionRequestDialog
import snd.komga.client.book.KomgaBookId

@Composable
fun OfflineDownloadsContent(
    storageLocation: PlatformFile?,
    onStorageLocationChange: (PlatformFile) -> Unit,
    onStorageLocationReset: () -> Unit,
    downloads: Collection<TaskEntry>,
    onDownloadPause: (KomgaBookId) -> Unit,
    onDownloadCancel: (KomgaBookId) -> Unit,
    onDownloadRetry: (KomgaBookId) -> Unit,
    onTaskRemove: (KomgaBookId) -> Unit,
    onTaskRemoveWithFiles: (KomgaBookId) -> Unit,
    onOpenLogs: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        storageLocation?.let {
            Column(verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
                Text(stringResource(Res.string.settings_offline_mode_storage_location))
                Text(
                    rememberStorageLabel(it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        var showDirectoryPickerDialog by remember { mutableStateOf(false) }
        if (showDirectoryPickerDialog) {
            StoragePermissionRequestDialog { directory ->
                directory?.let(onStorageLocationChange)
                showDirectoryPickerDialog = false
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
            Button(onClick = { showDirectoryPickerDialog = true }) {
                Text(stringResource(Res.string.settings_offline_mode_storage_location_change))
            }
            Button(onClick = onStorageLocationReset) {
                Text(stringResource(Res.string.settings_offline_mode_storage_location_reset))
            }
        }

        if (downloads.isEmpty()) {
            Text(
                stringResource(Res.string.download_tasks_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DownloadTasksSummary(downloads)
            downloads.forEach { entry ->
                DownloadTaskCard(
                    entry = entry,
                    onPause = onDownloadPause,
                    onCancel = onDownloadCancel,
                    onRetry = onDownloadRetry,
                    onRemove = onTaskRemove,
                    onRemoveWithFiles = onTaskRemoveWithFiles,
                    onOpenLogs = onOpenLogs,
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    entry: TaskEntry,
    onPause: (KomgaBookId) -> Unit,
    onCancel: (KomgaBookId) -> Unit,
    onRetry: (KomgaBookId) -> Unit,
    onRemove: (KomgaBookId) -> Unit,
    onRemoveWithFiles: (KomgaBookId) -> Unit,
    onOpenLogs: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val bookId = (entry.task as DownloadBook).bookId
    var showRemoveFilesConfirm by remember(entry.uniqueName) { mutableStateOf(false) }
    if (showRemoveFilesConfirm) {
        ConfirmationDialog(
            title = stringResource(Res.string.download_task_remove_files_confirm_title),
            body = stringResource(
                Res.string.download_task_remove_files_confirm_body,
                entry.displayTitle ?: bookId.value,
            ),
            onDialogConfirm = {
                showRemoveFilesConfirm = false
                onRemoveWithFiles(bookId)
            },
            onDialogDismiss = { showRemoveFilesConfirm = false },
            buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(layout.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.displayTitle ?: bookId.value, style = MaterialTheme.typography.titleSmall)
                    Text(
                        statusLabel(entry.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor(entry.status),
                    )
                }
                TaskActions(
                    status = entry.status,
                    bookId = bookId,
                    onPause = onPause,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onRemove = onRemove,
                    onRemoveWithFiles = { showRemoveFilesConfirm = true },
                    onOpenLogs = onOpenLogs,
                )
            }
            if (entry.status == TaskStatus.RUNNING || entry.status == TaskStatus.PAUSED) {
                if (entry.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { entry.completedBytes.toFloat() / entry.totalBytes },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        buildString {
                            append("${toMiB(entry.completedBytes)} MiB / ${toMiB(entry.totalBytes)} MiB")
                            if (entry.speedBytesPerSecond > 0) {
                                append(" · ")
                                append(stringResource(Res.string.download_task_speed, toMiB(entry.speedBytesPerSecond)))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            entry.errorMessage?.takeIf { entry.status == TaskStatus.FAILED }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TaskActions(
    status: TaskStatus,
    bookId: KomgaBookId,
    onPause: (KomgaBookId) -> Unit,
    onCancel: (KomgaBookId) -> Unit,
    onRetry: (KomgaBookId) -> Unit,
    onRemove: (KomgaBookId) -> Unit,
    onRemoveWithFiles: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    when (status) {
        TaskStatus.NEW -> ActionIcon(Icons.Rounded.Close, Res.string.download_task_cancel) { onCancel(bookId) }
        TaskStatus.RUNNING -> {
            ActionIcon(Icons.Rounded.Pause, Res.string.download_task_pause) { onPause(bookId) }
            ActionIcon(Icons.Rounded.Close, Res.string.download_task_cancel) { onCancel(bookId) }
        }
        TaskStatus.PAUSED -> {
            ActionIcon(Icons.Rounded.PlayArrow, Res.string.download_task_resume) { onRetry(bookId) }
            ActionIcon(Icons.Rounded.Close, Res.string.download_task_cancel) { onCancel(bookId) }
        }
        TaskStatus.FAILED, TaskStatus.CANCELED -> {
            ActionIcon(Icons.Rounded.Refresh, Res.string.download_task_retry) { onRetry(bookId) }
            if (status == TaskStatus.FAILED) {
                ActionIcon(Icons.AutoMirrored.Rounded.ReceiptLong, Res.string.download_task_view_logs, onOpenLogs)
            }
            ActionIcon(Icons.Rounded.DeleteOutline, Res.string.download_task_remove) { onRemove(bookId) }
        }
        TaskStatus.COMPLETED -> {
            ActionIcon(Icons.Rounded.DeleteOutline, Res.string.download_task_remove) { onRemove(bookId) }
            ActionIcon(Icons.Rounded.DeleteForever, Res.string.download_task_remove_files, onRemoveWithFiles)
        }
    }
}

@Composable
private fun DownloadTasksSummary(downloads: Collection<TaskEntry>) {
    val summary = remember(downloads) { summarizeDownloadTasks(downloads) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(LocalKomeliaLayout.current.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.controlSpacing),
        ) {
            Text(
                stringResource(
                    Res.string.download_tasks_summary,
                    summary.total,
                    summary.remaining,
                    summary.failed,
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            LinearProgressIndicator(
                progress = { summary.completed.toFloat() / summary.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(
                    Res.string.download_tasks_overall_progress,
                    summary.completed,
                    summary.total,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal data class DownloadTaskSummary(
    val total: Int,
    val completed: Int,
    val remaining: Int,
    val failed: Int,
)

internal fun summarizeDownloadTasks(downloads: Collection<TaskEntry>): DownloadTaskSummary = DownloadTaskSummary(
    total = downloads.size,
    completed = downloads.count { it.status == TaskStatus.COMPLETED },
    remaining = downloads.count { it.status !in setOf(TaskStatus.COMPLETED, TaskStatus.CANCELED) },
    failed = downloads.count { it.status == TaskStatus.FAILED },
)

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: org.jetbrains.compose.resources.StringResource,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun statusLabel(status: TaskStatus): String = stringResource(
    when (status) {
        TaskStatus.NEW -> Res.string.download_task_queued
        TaskStatus.RUNNING -> Res.string.download_task_running
        TaskStatus.PAUSED -> Res.string.download_task_paused
        TaskStatus.COMPLETED -> Res.string.download_task_completed
        TaskStatus.FAILED -> Res.string.download_task_failed
        TaskStatus.CANCELED -> Res.string.download_task_canceled
    }
)

@Composable
private fun statusColor(status: TaskStatus) = when (status) {
    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
    TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun toMiB(bytes: Long): String = (bytes.toFloat() / 1024 / 1024).formatDecimal(2)

@Composable
internal expect fun rememberStorageLabel(file: PlatformFile): String
