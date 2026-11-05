package snd.komelia.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.ReadingDesign
import snd.komelia.ui.common.components.KomeliaDropdownMenu
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.book.KomgaReadStatus

@Composable
internal fun LibraryBrowseControls(state: LibrarySeriesTabState) {
    val filter by state.filterState.state.collectAsState()
    val selectedStatuses = filter.readStatus.map { stringResource(AppStrings.forSeriesReadStatus(it)) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wrapControls = maxWidth < 360.dp * fontScale
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = ReadingDesign.browseInset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowseChoice(
                    label = if (filter.readStatus.isEmpty()) stringResource(Res.string.browse_all_status)
                        else selectedStatuses.joinToString(" · "),
                    modifier = Modifier.weight(1f),
                ) { dismiss ->
                    DropdownMenuItem(text = { Text(stringResource(Res.string.browse_all_status)) }, onClick = {
                        filter.readStatus.forEach(state.filterState::onReadStatusSelect)
                        dismiss()
                    })
                    KomgaReadStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(stringResource(AppStrings.forSeriesReadStatus(status))) },
                            trailingIcon = if (status in filter.readStatus) ({ Icon(Icons.Rounded.Check, null) }) else null,
                            onClick = { state.filterState.onReadStatusSelect(status) },
                        )
                    }
                }
                BrowseChoice(stringResource(AppStrings.forSeriesSort(filter.sortOrder)), Modifier.weight(if (wrapControls) 1.6f else 1.1f)) { dismiss ->
                    LibrarySeriesTabState.SeriesSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(AppStrings.forSeriesSort(sort))) },
                            trailingIcon = if (sort == filter.sortOrder) ({ Icon(Icons.Rounded.Check, null) }) else null,
                            onClick = {
                                state.filterState.onSortOrderChange(sort)
                                dismiss()
                            },
                        )
                    }
                }
                if (!wrapControls) LibraryCountAndDisplayControls(state)
            }
            if (wrapControls) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = ReadingDesign.browseInset),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryCountAndDisplayControls(state)
                }
            }
        }
    }
}

@Composable
private fun LibraryCountAndDisplayControls(state: LibrarySeriesTabState) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(pluralStringResource(Res.plurals.series_list_series_count, state.totalSeriesCount, state.totalSeriesCount),
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 60.dp * fontScale))
        IconButton(onClick = { state.onListChange(false) }, Modifier.size(48.dp).semantics { selected = !state.isList }) {
            Icon(Icons.Rounded.GridView, stringResource(Res.string.browse_grid), Modifier.size(20.dp),
                tint = if (!state.isList) colors.primary else colors.onSurfaceVariant)
        }
        IconButton(onClick = { state.onListChange(true) }, Modifier.size(48.dp).semantics { selected = state.isList }) {
            Icon(Icons.AutoMirrored.Rounded.ViewList, stringResource(Res.string.browse_list), Modifier.size(20.dp),
                tint = if (state.isList) colors.primary else colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun BrowseChoice(label: String, modifier: Modifier, content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false), color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        KomeliaDropdownMenu(expanded, onDismissRequest = { expanded = false }) { content { expanded = false } }
    }
}

@Composable
internal fun LibraryLayoutOptions(isList: Boolean, onListChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.browse_display_mode), Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        listOf(false, true).forEach { list ->
            val selected = isList == list
            Surface(onClick = { onListChange(list) }, color = Color.Transparent,
                modifier = Modifier.weight(1f).height(48.dp).semantics { this.selected = selected; role = Role.RadioButton }) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Surface(color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small, border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().height(43.dp).testTag(if (list) "library-list-choice-paint" else "library-grid-choice-paint")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (list) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, null, Modifier.size(20.dp))
                            Text(stringResource(if (list) Res.string.reading_list_view else Res.string.reading_grid_view), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
