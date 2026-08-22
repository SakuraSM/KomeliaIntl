package snd.komelia.ui.series.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_bulk_select_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_list_series_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.itemlist.SeriesLazyCardGrid
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.common.menus.bulk.BottomPopupBulkActionsPanel
import snd.komelia.ui.common.menus.bulk.BulkActionsContainer
import snd.komelia.ui.common.menus.bulk.SeriesBulkActionsContent
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.EXPANDED
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.series.SeriesFilterState
import snd.komelia.ui.series.view.SeriesFilterContent
import snd.komga.client.series.KomgaSeries

@Composable
fun SeriesListContent(
    series: List<KomgaSeries>,
    seriesTotalCount: Int,
    seriesActions: SeriesMenuActions,
    onSeriesClick: (KomgaSeries) -> Unit,

    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    selectedSeries: List<KomgaSeries>,
    onSeriesSelect: (KomgaSeries) -> Unit,

    isLoading: Boolean,
    filterState: SeriesFilterState?,

    totalPages: Int,
    currentPage: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,

    minSize: Dp,
) {
    Column {
        if (editMode) {
            BulkActionsToolbar(
                onCancel = { onEditModeChange(false) },
                series = series,
                selectedSeries = selectedSeries,
                onSeriesSelect = onSeriesSelect
            )
        }

        SeriesLazyCardGrid(
            series = series,
            onSeriesClick = if (editMode) onSeriesSelect else onSeriesClick,
            seriesMenuActions = if (editMode) null else seriesActions,

            selectedSeries = selectedSeries,
            onSeriesSelect = onSeriesSelect,

            totalPages = totalPages,
            currentPage = currentPage,
            onPageChange = onPageChange,

            beforeContent = {
                AnimatedVisibility(!editMode) {
                    ToolBar(
                        seriesTotalCount = seriesTotalCount,
                        pageSize = pageSize,
                        onPageSizeChange = onPageSizeChange,
                        isLoading = isLoading,
                        filterState = filterState
                    )
                }

            },
            minSize = minSize,
        )
        val width = LocalWindowWidth.current
        if ((width == COMPACT || width == MEDIUM) && selectedSeries.isNotEmpty()) {
            BottomPopupBulkActionsPanel {
                SeriesBulkActionsContent(selectedSeries, true)
            }
        }
    }
}

@Composable
private fun BulkActionsToolbar(
    onCancel: () -> Unit,
    series: List<KomgaSeries>,
    selectedSeries: List<KomgaSeries>,
    onSeriesSelect: (KomgaSeries) -> Unit,
) {
    BulkActionsContainer(
        onCancel = onCancel,
        selectedCount = selectedSeries.size,
        allSelected = series.size == selectedSeries.size,
        onSelectAll = {
            if (series.size == selectedSeries.size) series.forEach { onSeriesSelect(it) }
            else series.filter { it !in selectedSeries }.forEach { onSeriesSelect(it) }
        }
    ) {
        when (LocalWindowWidth.current) {
            FULL, EXPANDED -> {
                if (selectedSeries.isEmpty()) {
                    Text(stringResource(Res.string.series_bulk_select_desc))
                } else {
                    Spacer(Modifier.weight(1f))
                    SeriesBulkActionsContent(selectedSeries, false)
                }
            }

            COMPACT, MEDIUM -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolBar(
    seriesTotalCount: Int,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
    isLoading: Boolean,
    filterState: SeriesFilterState?,
) {
    val layout = LocalKomeliaLayout.current
    val platform = LocalPlatform.current
    val widthClass = LocalWindowWidth.current
    Box {
        if (isLoading) {
            LinearProgressIndicator(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().animateContentSize(),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var showFilters by remember { mutableStateOf(false) }

            if (filterState != null && showFilters && platform == MOBILE) {
                ModalBottomSheet(
                    onDismissRequest = { showFilters = false },
                ) {
                    Text(
                        text = stringResource(Res.string.series_filter_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = layout.dialogContentPadding),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.9f)
                            .verticalScroll(rememberScrollState())
                            .padding(layout.dialogContentPadding),
                    ) {
                        SeriesFilterContent(
                            filterState = filterState,
                            onDismiss = { showFilters = false },
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                if (seriesTotalCount != 0) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                pluralStringResource(
                                    Res.plurals.series_list_series_count,
                                    seriesTotalCount,
                                    seriesTotalCount
                                )
                            )
                        },
                    )

                    Spacer(Modifier.weight(1f))

                    if (filterState != null) {
                        val color =
                            if (filterState.isChanged) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.primary

                        Box {
                            IconButton(
                                onClick = { showFilters = true },
                                modifier = Modifier.cursorForHand(),
                            ) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(Res.string.series_filter_title),
                                    tint = color,
                                )
                            }
                            if (platform != MOBILE) {
                                val panelWidth = when (widthClass) {
                                    COMPACT -> 360.dp
                                    MEDIUM -> 520.dp
                                    EXPANDED, FULL -> 720.dp
                                }
                                DropdownMenu(
                                    expanded = showFilters,
                                    onDismissRequest = { showFilters = false },
                                    scrollState = rememberScrollState(),
                                    modifier = Modifier
                                        .width(panelWidth)
                                        .heightIn(max = 680.dp)
                                        .padding(layout.dialogContentPadding),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.series_filter_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SeriesFilterContent(
                                        filterState = filterState,
                                        onDismiss = { showFilters = false },
                                    )
                                }
                            }
                        }
                    }

                    PageSizeSelectionDropdown(pageSize, onPageSizeChange)
                }
            }
        }
    }
}
