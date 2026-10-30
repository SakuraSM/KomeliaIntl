package snd.komelia.ui.local

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import snd.komelia.offline.tasks.model.TaskData.DownloadBook
import snd.komelia.offline.tasks.model.TaskEntry
import snd.komelia.offline.tasks.model.TaskEntry.TaskStatus
import snd.komelia.ui.common.components.KomeliaDropdownMenu
import snd.komelia.ui.settings.offline.downloads.OfflineDownloadsState

@Composable
internal fun LocalDownloadTasks(state: OfflineDownloadsState, onManageDownloads: () -> Unit) {
    val tasks = state.downloads.collectAsState().value
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.reading_download_tasks), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onManageDownloads) { Text(stringResource(Res.string.actions_show_more)) }
    }
    if (tasks.isEmpty()) Text(stringResource(Res.string.download_tasks_empty),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    tasks.filter { it.task is DownloadBook }.forEach { entry ->
        LocalDownloadTaskRow(entry, state, onManageDownloads)
    }
}

@Composable
private fun LocalDownloadTaskRow(entry: TaskEntry, state: OfflineDownloadsState, onManageDownloads: () -> Unit) {
    val bookId = (entry.task as DownloadBook).bookId
    val isActive = entry.status == TaskStatus.NEW || entry.status == TaskStatus.RUNNING
    val canResume = entry.status in listOf(TaskStatus.PAUSED, TaskStatus.FAILED, TaskStatus.CANCELED)
    var showMenu by remember(entry.uniqueName) { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Download, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(entry.displayTitle ?: stringResource(Res.string.reading_download_tasks), style = MaterialTheme.typography.bodyMedium)
                val statusLabel = stringResource(when (entry.status) {
                    TaskStatus.NEW -> Res.string.download_task_queued
                    TaskStatus.RUNNING -> Res.string.download_task_running
                    TaskStatus.PAUSED -> Res.string.download_task_paused
                    TaskStatus.COMPLETED -> Res.string.download_task_completed
                    TaskStatus.FAILED -> Res.string.download_task_failed
                    TaskStatus.CANCELED -> Res.string.download_task_canceled
                })
                val progress = if (entry.totalBytes > 0) (entry.completedBytes.toFloat() / entry.totalBytes).coerceIn(0f, 1f) else null
                Text(if (progress != null && entry.status != TaskStatus.COMPLETED) "$statusLabel ${(progress * 100).roundToInt()}%" else statusLabel,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (progress != null && isActive) LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp), drawStopIndicator = {})
            }
            if (isActive) IconButton(onClick = { state.onDownloadPause(bookId) }) {
                Icon(Icons.Rounded.Pause, stringResource(Res.string.download_task_pause))
            }
            else if (canResume) IconButton(onClick = { state.onDownloadRetry(bookId) }) {
                Icon(Icons.Rounded.Refresh, stringResource(Res.string.download_task_retry))
            }
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.actions_show_more)) }
                KomeliaDropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                    if (isActive || entry.status == TaskStatus.PAUSED) DropdownMenuItem(
                        text = { Text(stringResource(Res.string.download_task_cancel)) },
                        onClick = { state.onDownloadCancel(bookId); showMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.reading_downloads_cache)) },
                        onClick = { showMenu = false; onManageDownloads() })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
