package snd.komelia.ui.home.edit.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_add_filter
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_collapse
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_delete
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_delete_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_drag
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_edit_done
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_expand
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_group_summary
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_groups
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_groups_description
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_label
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_reset_to_default
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_reset_to_default_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.KomeliaIconButton
import snd.komelia.ui.common.components.KomeliaIconButtonSize
import snd.komelia.ui.common.components.KomeliaIconButtonStyle
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.home.edit.BookCustomFilterState
import snd.komelia.ui.home.edit.BookFilterEditState
import snd.komelia.ui.home.edit.BookOnDeckFilterState
import snd.komelia.ui.home.edit.FilterEditState
import snd.komelia.ui.home.edit.FilterEditViewModel
import snd.komelia.ui.home.edit.SeriesCustomFilterState
import snd.komelia.ui.home.edit.SeriesFilterEditState
import snd.komelia.ui.home.edit.SeriesRecentlyAddedFilterState
import snd.komelia.ui.home.edit.SeriesRecentlyUpdatedFilterState
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.platform.cursorForMove
import snd.komelia.ui.strings.localizedEnumLabel

@Composable
fun FilterEditContent(
    filters: List<FilterEditState>,
    onFilterMove: (Int, Int) -> Unit,
    onExit: () -> Unit,
    onEditEnd: () -> Unit,
    onFilterAdd: (FilterEditViewModel.FilterType) -> Unit,
    onFilterRemove: (FilterEditState) -> Unit,
    onFiltersReset: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Toolbar(onExit, onEditEnd, onFiltersReset)
        EditContent(
            filters = filters,
            onFilterAdd = onFilterAdd,
            onFilterRemove = onFilterRemove,
            onFilterMove = onFilterMove,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Toolbar(
    onExit: () -> Unit,
    onEditEnd: () -> Unit,
    onReset: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = layout.pageHorizontalPadding, vertical = layout.controlSpacing),
        horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                stringResource(Res.string.navigation_back),
            )
        }
        Text(
            stringResource(Res.string.home_filter_groups),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        FilledTonalButton(
            onClick = { onEditEnd() },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Text(stringResource(Res.string.home_filter_edit_done))
        }

        var showResetDialog by remember { mutableStateOf(false) }
        IconButton(onClick = { showResetDialog = true }) {
            Icon(
                Icons.Rounded.Restore,
                stringResource(Res.string.home_filter_reset_to_default),
            )
        }
        if (showResetDialog) {
            ConfirmationDialog(
                body = stringResource(Res.string.home_filter_reset_to_default_confirm),
                onDialogConfirm = onReset,
                onDialogDismiss = { showResetDialog = false }
            )
        }
    }
}

@Composable
private fun EditContent(
    filters: List<FilterEditState>,
    onFilterAdd: (FilterEditViewModel.FilterType) -> Unit,
    onFilterRemove: (FilterEditState) -> Unit,
    onFilterMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalKomeliaLayout.current
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        filterReorderIndices(from.index, to.index, filters.size)?.let { (fromIndex, toIndex) ->
            onFilterMove(fromIndex, toIndex)
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(
            top = layout.controlSpacing,
            bottom = layout.gridBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        modifier = modifier.imePadding()
    ) {
        item("description") {
            Text(
                text = stringResource(Res.string.home_filter_groups_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontalPadding),
            )
        }
        items(filters, key = { it.hashCode() }) { data ->
            ReorderableItem(reorderableLazyListState, key = data.hashCode()) { isDragging ->
                FilterContent(
                    filterState = data,
                    isDragging = isDragging,
                    onFilterRemove = { onFilterRemove(data) }
                )
            }
        }
        item {
            AddConditionButton(
                onFilterAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontalPadding)
                    .animateItem(),
            )
        }
    }
}

internal fun filterReorderIndices(
    fromLazyListIndex: Int,
    toLazyListIndex: Int,
    filterCount: Int,
): Pair<Int, Int>? {
    val fromIndex = fromLazyListIndex - FILTER_LIST_HEADER_COUNT
    val toIndex = toLazyListIndex - FILTER_LIST_HEADER_COUNT
    return if (fromIndex in 0 until filterCount && toIndex in 0 until filterCount) {
        fromIndex to toIndex
    } else {
        null
    }
}

private const val FILTER_LIST_HEADER_COUNT = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConditionButton(
    onConditionAdd: (FilterEditViewModel.FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    var dropDownExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = dropDownExpanded,
        onExpandedChange = { dropDownExpanded = it },
        modifier = modifier,
    ) {
        FilledTonalButton(
            onClick = { dropDownExpanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .cursorForHand()
                .menuAnchor(PrimaryNotEditable)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(Res.string.home_filter_add_filter))
        }

        ExposedDropdownMenu(
            expanded = dropDownExpanded,
            onDismissRequest = { dropDownExpanded = false },
            modifier = Modifier.widthIn(min = 200.dp)
        ) {
            FilterEditViewModel.FilterType.entries.forEach {
                DropdownMenuItem(
                    text = { Text(localizedEnumLabel(it, it.name)) },
                    onClick = {
                        dropDownExpanded = false
                        onConditionAdd(it)
                    },
                    modifier = Modifier.cursorForHand()
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.FilterContent(
    filterState: FilterEditState,
    isDragging: Boolean,
    onFilterRemove: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val label = filterState.label.collectAsState().value
    var labelText by remember { mutableStateOf(label) }
    val groupType = when (filterState) {
        is BookFilterEditState -> FilterEditViewModel.FilterType.Book
        is SeriesFilterEditState -> FilterEditViewModel.FilterType.Series
    }
    val filterType = when (filterState) {
        is BookFilterEditState -> filterState.type.collectAsState().value
        is SeriesFilterEditState -> filterState.type.collectAsState().value
    }
    val summary = stringResource(
        Res.string.home_filter_group_summary,
        localizedEnumLabel(groupType, groupType.name),
        localizedEnumLabel(filterType, filterType.name),
    )
    Column(
        modifier = Modifier
            .padding(horizontal = layout.pageHorizontalPadding)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.surfaceBright
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .then(
                if (isDragging) Modifier.border(
                    3.dp,
                    MaterialTheme.colorScheme.secondary,
                    RoundedCornerShape(16.dp)
                )
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = layout.controlSpacing,
                    end = layout.controlSpacing,
                    top = layout.controlSpacing,
                    bottom = layout.controlSpacing,
                ),
            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(layout.minimumTouchTarget)
                    .draggableHandle()
                    .cursorForMove(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(Res.string.home_filter_drag),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            KomeliaIconButton(
                imageVector = if (showEdit) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (showEdit) Res.string.home_filter_collapse else Res.string.home_filter_expand
                ),
                onClick = { showEdit = !showEdit },
                style = if (showEdit) KomeliaIconButtonStyle.Tonal else KomeliaIconButtonStyle.Standard,
                size = KomeliaIconButtonSize.Regular,
            )
            KomeliaIconButton(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(Res.string.home_filter_delete),
                onClick = { showDeleteConfirmation = true },
                style = KomeliaIconButtonStyle.Danger,
                size = KomeliaIconButtonSize.Regular,
            )
        }

        AnimatedVisibility(showEdit) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = layout.cardContentPadding,
                        end = layout.cardContentPadding,
                        bottom = layout.cardContentPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
            ) {
                OutlinedTextField(
                    value = labelText,
                    label = { Text(stringResource(Res.string.home_filter_label)) },
                    onValueChange = {
                        labelText = it
                        filterState.label.value = it
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (filterState) {
                    is BookFilterEditState -> BookFilterEditContent(filterState)
                    is SeriesFilterEditState -> SeriesFilterEditContent(filterState)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        ConfirmationDialog(
            body = stringResource(Res.string.home_filter_delete_confirm, label),
            onDialogConfirm = onFilterRemove,
            onDialogDismiss = { showDeleteConfirmation = false })
    }
}

@Composable
private fun BookFilterEditContent(state: BookFilterEditState) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
        val filter = state.filter.collectAsState().value
        val type = state.type.collectAsState().value
        DropdownChoiceMenu(
            selectedOption = LabeledEntry(type, type.name),
            options = remember { BookFilterEditState.FilterType.entries.map { LabeledEntry(it, it.name) } },
            onOptionChange = { state.onTypeChange(it.value) },
        )

        when (filter) {
            is BookCustomFilterState -> BookConditionContent(filter)
            is BookOnDeckFilterState -> PageSizeSettingsContent(
                pageSize = filter.pageSize.collectAsState().value,
                onPageSizeChange = filter::onPageSizeChange
            )
        }

        val books = state.books.collectAsState().value
        val cardWidth = state.cardWidth.collectAsState().value
        LazyRow(
            contentPadding = PaddingValues(vertical = layout.controlSpacing),
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing)
        ) {
            items(books) {
                BookImageCard(book = it, modifier = Modifier.width(cardWidth))
            }
        }
    }
}

@Composable
private fun SeriesFilterEditContent(state: SeriesFilterEditState) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
        val filter = state.filter.collectAsState().value
        val type = state.type.collectAsState().value
        DropdownChoiceMenu(
            selectedOption = LabeledEntry(type, type.name),
            options = remember { SeriesFilterEditState.FilterType.entries.map { LabeledEntry(it, it.name) } },
            onOptionChange = { state.onTypeChange(it.value) },
        )

        when (filter) {
            is SeriesCustomFilterState -> SeriesConditionContent(filter)
            is SeriesRecentlyAddedFilterState -> PageSizeSettingsContent(
                pageSize = filter.pageSize.collectAsState().value,
                onPageSizeChange = filter::onPageSizeChange
            )

            is SeriesRecentlyUpdatedFilterState -> PageSizeSettingsContent(
                pageSize = filter.pageSize.collectAsState().value,
                onPageSizeChange = filter::onPageSizeChange
            )
        }

        val books = state.series.collectAsState().value
        val cardWidth = state.cardWidth.collectAsState().value
        LazyRow(
            contentPadding = PaddingValues(vertical = layout.controlSpacing),
            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing)
        ) {
            items(books) {
                SeriesImageCard(series = it, modifier = Modifier.width(cardWidth))
            }
        }
    }
}
