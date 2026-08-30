package snd.komelia.ui.settings.offline.cache

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.DefaultDateTimeFormats.toSystemTimeString
import snd.komelia.formatDecimal
import snd.komelia.ui.LoadState
import snd.komelia.ui.dialogs.ConfirmationDialog
import kotlin.time.Instant

private enum class CacheListMode { SERIES, BOOKS }

private sealed interface DeleteTarget {
    data class Book(val id: String, val title: String) : DeleteTarget
    data class Series(val id: String, val title: String) : DeleteTarget
    data object All : DeleteTarget
}

@Composable
internal fun OfflineCacheContent(
    catalog: OfflineCacheCatalog,
    loadState: LoadState<Unit>,
    selectedMediaKind: OfflineCacheMediaKind?,
    onMediaKindSelect: (OfflineCacheMediaKind?) -> Unit,
    onRetry: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onDeleteSeries: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    if (loadState is LoadState.Error) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    loadState.exception.message ?: stringResource(Res.string.error_unknown),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                FilledTonalButton(onClick = onRetry) {
                    Text(stringResource(Res.string.error_reload))
                }
            }
        }
        return
    }

    val filtered = remember(catalog, selectedMediaKind) { catalog.filtered(selectedMediaKind) }
    var listMode by remember { mutableStateOf(CacheListMode.SERIES) }
    var deleteTarget by remember { mutableStateOf<DeleteTarget?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CacheSummary(catalog = catalog, onDeleteAll = { deleteTarget = DeleteTarget.All })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CacheFilterChip(null, selectedMediaKind, stringResource(Res.string.settings_offline_cache_all), onMediaKindSelect)
            CacheFilterChip(OfflineCacheMediaKind.COMIC, selectedMediaKind, stringResource(Res.string.settings_offline_cache_comics), onMediaKindSelect)
            CacheFilterChip(OfflineCacheMediaKind.EPUB, selectedMediaKind, stringResource(Res.string.settings_offline_cache_epub), onMediaKindSelect)
            CacheFilterChip(OfflineCacheMediaKind.PDF, selectedMediaKind, stringResource(Res.string.settings_offline_cache_pdf), onMediaKindSelect)
            CacheFilterChip(OfflineCacheMediaKind.OTHER, selectedMediaKind, stringResource(Res.string.settings_offline_cache_other), onMediaKindSelect)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = listMode == CacheListMode.SERIES,
                onClick = { listMode = CacheListMode.SERIES },
                label = { Text(stringResource(Res.string.settings_offline_cache_series_view)) },
            )
            FilterChip(
                selected = listMode == CacheListMode.BOOKS,
                onClick = { listMode = CacheListMode.BOOKS },
                label = { Text(stringResource(Res.string.settings_offline_cache_books_view)) },
            )
        }

        if (loadState == LoadState.Loading && catalog.bookCount == 0) {
            Text(stringResource(Res.string.settings_offline_cache_loading))
        } else if (filtered.bookCount == 0) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.settings_offline_cache_empty), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(Res.string.settings_offline_cache_empty_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (listMode == CacheListMode.SERIES) {
            filtered.series.forEach { item ->
                CacheSeriesRow(
                    item = item,
                    onOpen = { onOpenSeries(item.id) },
                    onDelete = { deleteTarget = DeleteTarget.Series(item.id, item.title) },
                )
            }
            if (filtered.orphanBooks.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.settings_offline_cache_uncategorized), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(Res.string.settings_offline_cache_books, filtered.orphanBooks.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            filtered.books.forEach { book ->
                CacheBookRow(
                    book = book,
                    onOpen = { onOpenBook(book.id) },
                    onDelete = { deleteTarget = DeleteTarget.Book(book.id, book.title) },
                )
            }
        }
    }

    deleteTarget?.let { target ->
        val body = when (target) {
            is DeleteTarget.Book -> stringResource(Res.string.settings_offline_cache_delete_book_confirm, target.title)
            is DeleteTarget.Series -> stringResource(Res.string.settings_offline_cache_delete_series_confirm, target.title)
            DeleteTarget.All -> stringResource(Res.string.settings_offline_cache_delete_all_confirm)
        }
        ConfirmationDialog(
            title = stringResource(Res.string.settings_offline_cache_delete),
            body = body,
            buttonConfirm = stringResource(Res.string.settings_offline_cache_delete),
            buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,
            onDialogConfirm = {
                when (target) {
                    is DeleteTarget.Book -> onDeleteBook(target.id)
                    is DeleteTarget.Series -> onDeleteSeries(target.id)
                    DeleteTarget.All -> onDeleteAll()
                }
            },
            onDialogDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun CacheSummary(catalog: OfflineCacheCatalog, onDeleteAll: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_offline_cache_summary, catalog.bookCount, formatBytes(catalog.totalSizeBytes)),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (catalog.missingBookCount > 0) {
                    Text(
                        stringResource(Res.string.settings_offline_cache_missing_summary, catalog.missingBookCount),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            OutlinedButton(onClick = onDeleteAll, enabled = catalog.bookCount > 0) {
                Icon(Icons.Default.DeleteOutline, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.settings_offline_cache_delete_all))
            }
        }
    }
}

@Composable
private fun CacheSeriesRow(item: OfflineCacheSeriesItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${stringResource(Res.string.settings_offline_cache_books, item.books.size)} · ${formatBytes(item.sizeBytes)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.missingBookCount > 0) {
                    Text(stringResource(Res.string.settings_offline_cache_missing_summary, item.missingBookCount), color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onOpen) { Text(stringResource(Res.string.settings_offline_cache_open)) }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(Res.string.settings_offline_cache_delete))
            }
        }
    }
}

@Composable
private fun CacheBookRow(book: OfflineCacheBookRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (book.isAvailable) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.BrokenImage, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${cacheMediaKindLabel(book.mediaKind)} · ${formatBytes(book.sizeBytes)} · ${Instant.fromEpochSeconds(book.updatedEpochSeconds).toSystemTimeString()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!book.isAvailable) Text(stringResource(Res.string.settings_offline_cache_missing), color = MaterialTheme.colorScheme.error)
            }
            FilledTonalButton(onClick = onOpen, enabled = book.isAvailable) {
                Text(stringResource(Res.string.settings_offline_cache_open))
            }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(Res.string.settings_offline_cache_delete))
            }
        }
    }
}

@Composable
private fun CacheFilterChip(
    kind: OfflineCacheMediaKind?,
    selected: OfflineCacheMediaKind?,
    label: String,
    onSelect: (OfflineCacheMediaKind?) -> Unit,
) {
    FilterChip(selected = selected == kind, onClick = { onSelect(kind) }, label = { Text(label) })
}

@Composable
private fun cacheMediaKindLabel(kind: OfflineCacheMediaKind): String = when (kind) {
    OfflineCacheMediaKind.COMIC -> stringResource(Res.string.settings_offline_cache_comics)
    OfflineCacheMediaKind.EPUB -> stringResource(Res.string.settings_offline_cache_epub)
    OfflineCacheMediaKind.PDF -> stringResource(Res.string.settings_offline_cache_pdf)
    OfflineCacheMediaKind.OTHER -> stringResource(Res.string.settings_offline_cache_other)
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "${(bytes.toDouble() / 1024 / 1024 / 1024).formatDecimal(2)} GiB"
    bytes >= 1024L * 1024L -> "${(bytes.toDouble() / 1024 / 1024).formatDecimal(2)} MiB"
    bytes >= 1024L -> "${(bytes.toDouble() / 1024).formatDecimal(2)} KiB"
    else -> "$bytes B"
}
