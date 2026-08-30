package snd.komelia.ui.common.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_source
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.OutlinedText
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.platform.cursorForHand

const val defaultCardWidth = 240

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    image: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
            .then(if (onClick != null || onLongClick != null) Modifier.cursorForHand() else Modifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.aspectRatio(0.703f)) { image() }
        content()
    }
}

@Composable
fun ItemCardWithContent(
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.aspectRatio(0.703f)) { image() }
        content()
    }
}

enum class CoverCaptionVariant {
    TitleOnly,
    TitleWithSupporting,
}

fun coverCaptionHeight(variant: CoverCaptionVariant, platform: PlatformType) = when (variant) {
    CoverCaptionVariant.TitleOnly -> if (platform == PlatformType.MOBILE) 48.dp else 52.dp
    CoverCaptionVariant.TitleWithSupporting -> if (platform == PlatformType.MOBILE) 68.dp else 72.dp
}

data class MetadataTagSummary(
    val visible: List<String>,
    val hiddenCount: Int,
)

fun metadataTagLimit(width: WindowSizeClass): Int = when (width) {
    WindowSizeClass.COMPACT -> 3
    WindowSizeClass.MEDIUM -> 5
    WindowSizeClass.EXPANDED, WindowSizeClass.FULL -> 8
}

fun summarizeMetadataTags(values: List<String>, limit: Int): MetadataTagSummary {
    val normalized = values.map(String::trim).filter(String::isNotEmpty).distinct()
    return MetadataTagSummary(
        visible = normalized.take(limit),
        hiddenCount = (normalized.size - limit).coerceAtLeast(0),
    )
}

@Composable
fun MetadataTagFlow(
    values: List<String>,
    width: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    val summary = summarizeMetadataTags(values, metadataTagLimit(width))
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        summary.visible.forEach { value -> MetadataChip(value) }
        if (summary.hiddenCount > 0) MetadataChip("+${summary.hiddenCount}")
    }
}

@Composable
private fun MetadataChip(value: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 104.dp).padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun CoverCardCaption(
    title: String,
    variant: CoverCaptionVariant,
    supportingText: String? = null,
) {
    val platform = LocalPlatform.current
    val isMobile = platform == PlatformType.MOBILE
    val captionHeight = coverCaptionHeight(variant, platform)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(captionHeight)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = if (isMobile) {
                    MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
                } else {
                    MaterialTheme.typography.titleSmall
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (variant == CoverCaptionVariant.TitleWithSupporting) {
                Text(
                    text = supportingText.orEmpty(),
                    style = if (isMobile) {
                        MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LocalSourceBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.local_source),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun CardStatusBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CardGradientOverlay() {
    val colorStops = arrayOf(
        0.0f to Color.Black.copy(alpha = .5f),
        0.10f to Color.Transparent,
        0.6f to Color.Transparent,
        0.90f to Color.Black.copy(alpha = .8f),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colorStops = colorStops))
    )
}

@Composable
fun overlayBorderModifier() =
    Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.medium)


@Composable
fun CardOutlinedText(
    text: String,
    textColor: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
    outlineDrawStyle: Stroke = Stroke(4f),
) {
    OutlinedText(
        text = text,
        maxLines = maxLines,
        fillColor = textColor,
        outlineColor = Color.Black,
        style = style,
        overflow = TextOverflow.Ellipsis,
        outlineDrawStyle = outlineDrawStyle,
    )
}

@Composable
fun SelectionRadioButton(
    isSelected: Boolean,
    onSelect: () -> Unit,
) {

    RadioButton(
        selected = isSelected,
        onClick = onSelect,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .4f))
            .selectable(selected = isSelected, onClick = onSelect)
    )
}

@Composable
fun LazyGridItemScope.DraggableImageCard(
    key: String,
    dragEnabled: Boolean,
    reorderableState: ReorderableLazyGridState,
    content: @Composable () -> Unit
) {
    val platform = LocalPlatform.current
    if (dragEnabled) {
        ReorderableItem(reorderableState, key = key) {
            if (platform == PlatformType.MOBILE) {

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    content()
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .fillMaxWidth()
                            .draggableHandle()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.DragHandle, null) }
                }

            } else {
                Box(Modifier.draggableHandle()) { content() }
            }

        }
    } else content()
}
