package snd.komelia.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.platform.cursorForHand

@Composable
fun <T> DescriptionChips(
    label: String,
    chipValue: LabeledEntry<T>,
    onClick: (T) -> Unit = {},
    modifier: Modifier = Modifier
) {
    DescriptionChips(
        label = label,
        chipValues = listOf(chipValue),
        onChipClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> DescriptionChips(
    label: String,
    chipValues: List<LabeledEntry<T>>,
    secondaryValues: List<LabeledEntry<T>>? = null,
    onChipClick: (T) -> Unit = {},
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    if (chipValues.isEmpty() && secondaryValues.isNullOrEmpty()) return
    DetailMetadataRow(label = label, modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            chipValues.forEach { entry ->
                MetadataValueChip(onClick = { onChipClick(entry.value) }) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    icon?.let { Icon(it, null, modifier = Modifier.size(18.dp)) }
                }
            }
            secondaryValues?.filter { it !in chipValues }?.forEach { entry ->
                MetadataValueChip(
                    borderColor = MaterialTheme.colorScheme.primary,
                    onClick = { onChipClick(entry.value) }) {
                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                    icon?.let { Icon(it, null, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
fun MetadataValueChip(
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val motion = LocalKomeliaMotion.current
    val active = isHovered || isPressed || isFocused
    val containerColor by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(
            durationMillis = motion.duration(motion.stateDurationMillis),
            easing = motion.standardEasing,
        ),
        label = "metadataChipContainerColor",
    )
    val resolvedBorderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else borderColor,
        animationSpec = tween(
            durationMillis = motion.duration(motion.stateDurationMillis),
            easing = motion.standardEasing,
        ),
        label = "metadataChipBorderColor",
    )
    val layout = LocalKomeliaLayout.current

    Box(
        modifier = modifier
            .sizeIn(
                minWidth = layout.minimumTouchTarget,
                minHeight = layout.minimumTouchTarget,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .cursorForHand(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, resolvedBorderColor),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                content = content,
            )
        }
    }
}


@Composable
fun NoPaddingChip(
    borderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    color: Color = Color.Unspecified,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .border(Dp.Hairline, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable { onClick() }
            .padding(10.dp, 5.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            content()
        }
    }
}


object AppFilterChipDefaults {

    @Composable
    fun filterChipColors() = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
    )
}
