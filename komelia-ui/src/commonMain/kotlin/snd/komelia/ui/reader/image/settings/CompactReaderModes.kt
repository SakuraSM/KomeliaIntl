package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.primary)
    val layout = paged.layout.collectAsState().value
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(Res.string.reader_type), style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(colors.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(PageDisplayLayout.SINGLE_PAGE, PageDisplayLayout.DOUBLE_PAGES).forEach { target ->
                val selected = type == ReaderType.PAGED && (if (target == PageDisplayLayout.SINGLE_PAGE) layout == target else layout != PageDisplayLayout.SINGLE_PAGE)
                FilterChip(selected, onClick = {
                    if (onPagedLayout != null) onPagedLayout(if (selected) layout else target)
                    else { onType(ReaderType.PAGED); paged.onLayoutChange(target) }
                }, label = { Text(stringResource(AppStrings.forLayout(target))) },
                    modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp), colors = chipColors)
            }
            FilterChip(type == ReaderType.CONTINUOUS, onClick = { onType(ReaderType.CONTINUOUS) },
                label = { Text(stringResource(Res.string.reader_type_continuous)) }, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp), colors = chipColors)
        }
        if (panels != null) FilterChip(type == ReaderType.PANELS, onClick = { onType(ReaderType.PANELS) },
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
                        modifier = Modifier.heightIn(min = 48.dp), colors = chipColors)
                }
            }
        }
    }
}
