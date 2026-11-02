package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.*
import snd.komelia.ui.Theme
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.reader.image.continuous.ContinuousReaderState
import snd.komelia.ui.reader.image.paged.PagedReaderState
import snd.komelia.ui.reader.image.panels.PanelsReaderState
import snd.komelia.ui.strings.AppStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompactReaderModes(
    type: ReaderType,
    onType: (ReaderType) -> Unit,
    onPagedLayout: ((PageDisplayLayout) -> Unit)?,
    paged: PagedReaderState,
    continuous: ContinuousReaderState,
    panels: PanelsReaderState?,
) {
    val colors = Theme.valueOf(LocalSurfaceAppearance.current.theme.name).colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary,
        selectedLeadingIconColor = colors.onPrimary)
    val layout = paged.layout.collectAsState().value
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(colors.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(PageDisplayLayout.SINGLE_PAGE, PageDisplayLayout.DOUBLE_PAGES).forEach { target ->
                val selected = type == ReaderType.PAGED && (if (target == PageDisplayLayout.SINGLE_PAGE) layout == target else layout != PageDisplayLayout.SINGLE_PAGE)
                FilterChip(selected, onClick = {
                    if (onPagedLayout != null) onPagedLayout(if (selected) layout else target)
                    else { onType(ReaderType.PAGED); paged.onLayoutChange(target) }
                }, label = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(if (target == PageDisplayLayout.SINGLE_PAGE) Icons.Rounded.CropPortrait else Icons.Rounded.AutoStories, null, Modifier.size(20.dp))
                        Text(stringResource(AppStrings.forLayout(target)), style = MaterialTheme.typography.labelMedium)
                    }
                }, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp), shape = RoundedCornerShape(8.dp), colors = chipColors)
            }
            FilterChip(type == ReaderType.CONTINUOUS, onClick = { onType(ReaderType.CONTINUOUS) },
                label = {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.ViewDay, null, Modifier.size(20.dp))
                        Text(stringResource(Res.string.reader_type_continuous), style = MaterialTheme.typography.labelMedium)
                    }
                }, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 64.dp),
                shape = RoundedCornerShape(8.dp), colors = chipColors)
        }
        if (panels != null) FilterChip(type == ReaderType.PANELS, onClick = { onType(ReaderType.PANELS) },
            leadingIcon = { Icon(Icons.Rounded.Dashboard, null, Modifier.size(20.dp)) },
            label = { Text(stringResource(Res.string.reader_type_panels)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = chipColors)
        Text(stringResource(Res.string.reader_paged_reading_direction), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (type == ReaderType.CONTINUOUS) {
                val direction = continuous.readingDirection.collectAsState().value
                ContinuousReadingDirection.entries.forEach { target ->
                    FilterChip(direction == target, onClick = { continuous.onReadingDirectionChange(target) },
                        label = { Text(stringResource(AppStrings.forReadingDirection(target))) },
                        modifier = Modifier.heightIn(min = 48.dp), colors = chipColors)
                }
            } else {
                val direction = if (type == ReaderType.PANELS) panels?.readingDirection?.collectAsState()?.value
                    else paged.readingDirection.collectAsState().value
                PagedReadingDirection.entries.forEach { target ->
                    FilterChip(direction == target, onClick = {
                        if (type == ReaderType.PANELS) panels?.onReadingDirectionChange(target) else paged.onReadingDirectionChange(target)
                    }, label = { Text(stringResource(AppStrings.forReadingDirection(target))) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = chipColors)
                }
            }
        }
    }
}
