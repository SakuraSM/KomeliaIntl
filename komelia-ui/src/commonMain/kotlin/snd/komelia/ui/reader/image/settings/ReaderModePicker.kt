package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.*
import snd.komelia.ui.Theme
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.strings.AppStrings

internal data class ReaderModeSelection(
    val type: ReaderType,
    val layout: PageDisplayLayout,
    val pagedDirection: PagedReadingDirection?,
    val continuousDirection: ContinuousReadingDirection,
    val hasPanels: Boolean,
)
internal data class ReaderModePickerActions(
    val onPagedLayout: (PageDisplayLayout) -> Unit,
    val onType: (ReaderType) -> Unit,
    val onPagedDirection: (PagedReadingDirection) -> Unit,
    val onContinuousDirection: (ContinuousReadingDirection) -> Unit,
)

/** Presentation is independent of reader lifetimes; the adapter owns mode transitions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderModePicker(selection: ReaderModeSelection, actions: ReaderModePickerActions) {
    val colors = Theme.valueOf(LocalSurfaceAppearance.current.theme.name).colorScheme
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary,
        selectedLabelColor = colors.onPrimary, selectedLeadingIconColor = colors.onPrimary)
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(colors.surfaceContainerLow, RoundedCornerShape(12.dp))) {
            listOf(PageDisplayLayout.SINGLE_PAGE, PageDisplayLayout.DOUBLE_PAGES).forEach { target ->
                val selected = selection.type == ReaderType.PAGED &&
                    (if(target==PageDisplayLayout.SINGLE_PAGE)selection.layout==target else selection.layout!=PageDisplayLayout.SINGLE_PAGE)
                FilterChip(selected,onClick={actions.onPagedLayout(if(selected)selection.layout else target)},
                    label={Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(if(target==PageDisplayLayout.SINGLE_PAGE)Icons.Rounded.CropPortrait else Icons.Rounded.AutoStories,null,Modifier.size(20.dp))
                        Text(stringResource(AppStrings.forLayout(target)),style=MaterialTheme.typography.labelMedium)
                    }},modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=64.dp).padding(vertical=3.dp)
                        .testTag(if(target==PageDisplayLayout.SINGLE_PAGE)"reader-mode-single-paint"else"reader-mode-double-paint"),
                    shape=RoundedCornerShape(8.dp),colors=chipColors)
            }
            FilterChip(selection.type==ReaderType.CONTINUOUS,onClick={actions.onType(ReaderType.CONTINUOUS)},
                label={Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.ViewDay,null,Modifier.size(20.dp))
                    Text(stringResource(Res.string.reader_type_continuous),style=MaterialTheme.typography.labelMedium)
                }},modifier=Modifier.weight(1f).fillMaxHeight().heightIn(min=64.dp).padding(vertical=3.dp).testTag("reader-mode-continuous-paint"),
                shape=RoundedCornerShape(8.dp),colors=chipColors)
        }
        if(selection.hasPanels)FilterChip(selection.type==ReaderType.PANELS,onClick={actions.onType(ReaderType.PANELS)},
            leadingIcon={Icon(Icons.Rounded.Dashboard,null,Modifier.size(20.dp))},
            label={Text(stringResource(Res.string.reader_type_panels))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),colors=chipColors)
        Text(stringResource(Res.string.reader_paged_reading_direction),style=MaterialTheme.typography.bodyMedium,
            modifier=Modifier.offset(y=5.dp))
        Row(Modifier.fillMaxWidth().padding(top = 3.5.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(selection.type==ReaderType.CONTINUOUS) {
                ContinuousReadingDirection.entries.forEach { target ->
                    ReaderDirectionChoice(selection.continuousDirection == target, { actions.onContinuousDirection(target) },
                        Modifier.weight(1f)) { Text(stringResource(AppStrings.forReadingDirection(target))) }
                }
            }else {
                PagedReadingDirection.entries.forEach { target ->
                    ReaderDirectionChoice(selection.pagedDirection == target, { actions.onPagedDirection(target) },
                        Modifier.weight(1f), "reader-direction-${target.name.lowercase()}-paint") {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                            Icon(if(target==PagedReadingDirection.LEFT_TO_RIGHT)Icons.AutoMirrored.Rounded.ArrowForward else Icons.AutoMirrored.Rounded.ArrowBack,null,Modifier.size(20.dp))
                            Text(stringResource(AppStrings.forReadingDirection(target)),style=MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderDirectionChoice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    paintTag: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = Theme.valueOf(LocalSurfaceAppearance.current.theme.name).colorScheme
    val largeText = LocalDensity.current.fontScale > 1f
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected }) {
        Box(contentAlignment = Alignment.Center, modifier = if (largeText) Modifier.heightIn(min=48.dp) else Modifier.height(48.dp)) {
            Surface(
                color = if (selected) colors.primary else colors.surfaceContainerLow,
                contentColor = if (selected) colors.onPrimary else colors.onSurface,
                border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().then(if(largeText)Modifier.heightIn(min=43.dp)else Modifier.height(43.dp))
                    .then(if (paintTag != null) Modifier.testTag(paintTag) else Modifier),
            ) { Box(contentAlignment = Alignment.Center) { content() } }
        }
    }
}
