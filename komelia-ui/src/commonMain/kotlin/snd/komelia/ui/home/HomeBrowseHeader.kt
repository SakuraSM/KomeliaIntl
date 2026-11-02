package snd.komelia.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import snd.komelia.ui.common.components.KomeliaDropdownMenu as DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import snd.komelia.ui.common.components.KomeliaModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_all
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_group_items
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_groups
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_search_groups
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.lazy.LazyColumn
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_filter_all_groups
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import snd.komelia.ui.common.components.BrowsePageHeader
import snd.komelia.ui.common.components.komeliaHighlight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

@Composable
internal fun HomeBrowseHeader(
    filters: List<HomeFilterData>,
    recentlyReadBooks: List<KomeliaBook>,
    currentFilterNumber: Int,
    onFilterChange: (Int) -> Unit,
) {
    val toolbarFilters = remember(filters) { homeToolbarEntries(filters) }
    val allLabel = stringResource(Res.string.home_filter_all)
    val entries = listOf(HomeToolbarEntry(HOME_ALL_TAB_ID, allLabel, homeAllItemCount(filters, recentlyReadBooks))) + toolbarFilters
    val currentFilter = toolbarFilters.firstOrNull { it.id == currentFilterNumber }
    var pickerOpen by remember { mutableStateOf(false) }
    val useBottomSheet = LocalWindowWidth.current == snd.komelia.ui.platform.WindowSizeClass.COMPACT ||
        LocalWindowWidth.current == snd.komelia.ui.platform.WindowSizeClass.MEDIUM
    LaunchedEffect(currentFilterNumber, toolbarFilters) {
        if (currentFilterNumber != 0 && toolbarFilters.none { it.id == currentFilterNumber }) onFilterChange(0)
    }
    BrowsePageHeader(
        title = "Komelia",
        titleContent = { modifier -> Text("Komelia", style = MaterialTheme.typography.titleLarge,
            modifier = modifier.padding(start = 16.dp).offset(y = (-6).dp)) },
    ) {
        if (toolbarFilters.isNotEmpty()) {
            Box(Modifier.padding(end = 16.dp)) {
                Surface(onClick = { pickerOpen = true }, color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.width((108.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)).coerceAtMost(148.dp)).height(48.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxWidth().padding(top = 2.dp).height(32.dp),
                            shape = RoundedCornerShape(16.dp), border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(currentFilter?.label ?: stringResource(Res.string.home_filter_all_groups),
                                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.ExpandMore, stringResource(Res.string.home_filter_groups), Modifier.size(20.dp))
                            }
                        }
                    }
                }
                if (!useBottomSheet) DropdownMenu(pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    HomeGroupPickerItems(entries, currentFilterNumber, Modifier.width(320.dp)) {
                        pickerOpen = false
                        onFilterChange(it)
                    }
                }
            }
        }
    }
    if (useBottomSheet && pickerOpen) HomeGroupPickerSheet(entries, currentFilterNumber,
        onDismiss = { pickerOpen = false }, onFilterChange = {
            pickerOpen = false
            onFilterChange(it)
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeGroupPickerSheet(
    filters: List<HomeToolbarEntry>,
    currentFilterNumber: Int,
    onDismiss: () -> Unit,
    onFilterChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(
                horizontal = layout.dialogContentPadding,
                vertical = layout.controlSpacing,
            ),
            verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        ) {
            Text(stringResource(Res.string.home_filter_groups), style = MaterialTheme.typography.titleLarge)
            HomeGroupPickerItems(
                filters = filters,
                currentFilterNumber = currentFilterNumber,
                modifier = Modifier.fillMaxWidth(),
                onFilterChange = onFilterChange,
            )
        }
    }
}

@Composable
internal fun HomeGroupPickerItems(
    filters: List<HomeToolbarEntry>,
    currentFilterNumber: Int,
    modifier: Modifier = Modifier,
    onFilterChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    var query by remember { mutableStateOf("") }
    val visibleFilters = remember(filters, query) {
        if (query.isBlank()) filters else filters.filter { it.label.contains(query, ignoreCase = true) }
    }
    Column(modifier.heightIn(max = 480.dp)) {
        if (filters.size > 6) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(Res.string.home_filter_search_groups)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = layout.controlSpacing),
            )
        }
        // DropdownMenu measures intrinsic width. A regular bounded scroll column also works
        // in the compact sheet, where LazyColumn cannot answer those menu measurements.
        Column(Modifier.verticalScroll(rememberScrollState())) {
            visibleFilters.forEach { data ->
                DropdownMenuItem(
                    modifier = Modifier.komeliaHighlight(data.id == currentFilterNumber, MaterialTheme.shapes.small, inset = 4.dp)
                        .semantics { selected = data.id == currentFilterNumber },
                    text = {
                        Column {
                            Text(data.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(Res.string.home_filter_group_items, data.itemCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = if (data.id == currentFilterNumber) {
                        { Icon(Icons.Rounded.Check, null) }
                    } else null,
                    onClick = { onFilterChange(data.id) },
                )
            }
        }
    }
}

private fun HomeFilterData.itemCount(): Int = when (this) {
    is BookFilterData -> books.size
    is SeriesFilterData -> series.size
}

internal fun homeAllItemCount(filters: List<HomeFilterData>, recentlyReadBooks: List<KomeliaBook>): Int {
    if (recentlyReadBooks.isEmpty()) return filters.sumOf { it.itemCount() }
    val hero = mostRecentlyReadingBook(recentlyReadBooks) ?: homeFeaturedBook(filters, HOME_ALL_TAB_ID)?.book
    val extraHero = if (hero != null && recentlyReadBooks.none { it.id == hero.id }) 1 else 0
    return recentlyReadBooks.size + extraHero +
        filters.filterNot { isReadingOverviewFilter(it.filter) }.sumOf { it.itemCount() }
}

internal fun homeGroupToolbarFilters(filters: List<HomeFilterData>): List<HomeFilterData> =
    filters.sortedBy { it.filter.order }

internal const val HOME_ALL_TAB_ID = 0

internal data class HomeToolbarEntry(
    val id: Int,
    val label: String,
    val itemCount: Int,
)

internal fun homeToolbarEntries(
    filters: List<HomeFilterData>,
): List<HomeToolbarEntry> = homeGroupToolbarFilters(filters).map {
    HomeToolbarEntry(it.filter.order, it.filter.label, it.itemCount())
}
