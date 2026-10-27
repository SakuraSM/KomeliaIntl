package snd.komelia.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Uses the shared white selection light for toolbar choices while retaining native chip semantics. */
@Composable
fun ChromeFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    colors: SelectableChipColors = AppFilterChipDefaults.filterChipColors(),
    border: BorderStroke? = null,
) {
    val glass = LocalSurfaceAppearance.current.useGlass
    val interactions = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            ProvideTextStyle(LocalTextStyle.current.copy(
                fontWeight = if (glass && selected) FontWeight.SemiBold else LocalTextStyle.current.fontWeight,
            ), content = label)
        },
        leadingIcon = leadingIcon,
        modifier = modifier.komeliaHighlight(selected, MaterialTheme.shapes.small, interactionSource = interactions),
        interactionSource = interactions,
        colors = if (glass) FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
        ) else colors,
        border = if (glass) null else border,
    )
}
