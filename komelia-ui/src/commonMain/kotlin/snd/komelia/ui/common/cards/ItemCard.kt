package snd.komelia.ui.common.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyGridState
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.OutlinedText
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.cursorForHand

const val defaultCardWidth = 240

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(modifier = Modifier.aspectRatio(0.703f)) { image() }
        content()
    }
}

@Composable
fun CoverCardCaption(
    title: String,
    supportingText: String? = null,
    statusText: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!statusText.isNullOrBlank()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
