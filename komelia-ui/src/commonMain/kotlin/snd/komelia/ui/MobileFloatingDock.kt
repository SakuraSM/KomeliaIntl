package snd.komelia.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import kotlin.math.roundToInt

@Composable
internal fun MobileFloatingDock(
    selected: AppDestination,
    destinations: List<DestinationItem>,
    onSelect: (AppDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > 0.5f
    val dockColor = if (light) colors.surfaceContainerLow else lerp(colors.surfaceContainer, Color.Black, 0.36f)
    val insets = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(
        Modifier.fillMaxWidth().windowInsetsPadding(insets)
            .padding(horizontal = 9.dp).padding(top = 8.dp, bottom = 4.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("primary-navigation"),
            shape = RoundedCornerShape(16.dp),
            color = dockColor,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Layout(
                modifier = Modifier.fillMaxWidth().height(60.dp + 32.dp * (fontScale - 1f)).selectableGroup(),
                content = {
                    destinations.forEach { item ->
                        MobileDockItem(item, item.destination == selected, light) { onSelect(item.destination) }
                    }
                },
            ) { measurables, constraints ->
                // Round absolute edges so the centers stay symmetric at odd widths.
                fun edge(index: Int) = (constraints.maxWidth * index.toFloat() / measurables.size).roundToInt()
                val items = measurables.mapIndexed { index, measurable ->
                    measurable.measure(Constraints.fixed(edge(index + 1) - edge(index), constraints.maxHeight))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    items.forEachIndexed { index, item -> item.placeRelative(edge(index), 0) }
                }
            }
        }
    }
}

@Composable
private fun MobileDockItem(item: DestinationItem, selected: Boolean, light: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = LocalKomeliaMotion.current
    val target = if (selected) 1f else 0f
    val selection = animateFloatAsState(
        target,
        tween(motion.duration(motion.pressDurationMillis), easing = motion.standardEasing),
        label = "dockSelection",
    )
    val fraction = if (motion.isReducedMotion) target else selection.value
    val accent = colors.primary
    val iconAccent = if (light) accent else lerp(accent, Color.White, 0.24f)
    val reduceTransparency = LocalSurfaceAppearance.current.reduceTransparency
    val glowStrength = if (light) 0.19f else 0.64f
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()

    Box(
        modifier = Modifier.fillMaxSize()
            .testTag("primary-tab-${item.destination.name.lowercase()}")
            .selectable(selected, interactionSource = interactions, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(width = 50.dp, height = 26.dp).then(
                    if (focused) Modifier.border(1.dp, accent, RoundedCornerShape(10.dp)) else Modifier,
                ).drawWithCache {
                    val radius = 24.dp.toPx()
                    val reflection = Brush.radialGradient(
                        0f to accent.copy(alpha = glowStrength),
                        0.4f to accent.copy(alpha = glowStrength * 0.55f),
                        1f to accent.copy(alpha = 0f),
                        center = size.center,
                        radius = radius,
                    )
                    onDrawBehind {
                        val selectedAlpha = if (motion.isReducedMotion) target else selection.value
                        val alpha = maxOf(selectedAlpha, if (pressed) 0.25f else 0f)
                        if (alpha > 0f && !reduceTransparency) {
                            scale(scaleX = 1f, scaleY = 0.69f) {
                                drawCircle(reflection, radius, alpha = alpha)
                            }
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) item.selectedIcon else item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = lerp(colors.onSurfaceVariant, iconAccent, fraction),
                )
            }
            Text(
                text = stringResource(item.label),
                modifier = Modifier.padding(horizontal = 2.dp),
                color = lerp(colors.onSurfaceVariant, accent, fraction),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.padding(top = 2.dp).size(4.dp)
                    .testTag("navigation-selection-indicator")
                    .background(accent.copy(alpha = fraction), CircleShape),
            )
        }
    }
}
