package snd.komelia.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.ui.KomeliaSpacing
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.platform.cursorForHand

enum class KomeliaIconButtonStyle {
    Standard,
    Tonal,
    Danger,
}

enum class KomeliaIconButtonSize(val minimumSize: Dp, val iconSize: Dp) {
    Compact(minimumSize = 40.dp, iconSize = 20.dp),
    Regular(minimumSize = 48.dp, iconSize = 24.dp),
}

@Composable
fun KomeliaIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KomeliaIconButtonStyle = KomeliaIconButtonStyle.Standard,
    size: KomeliaIconButtonSize = KomeliaIconButtonSize.Compact,
    enabled: Boolean = true,
) {
    val colors = when (style) {
        KomeliaIconButtonStyle.Standard -> IconButtonDefaults.iconButtonColors()
        KomeliaIconButtonStyle.Tonal -> IconButtonDefaults.filledTonalIconButtonColors()
        KomeliaIconButtonStyle.Danger -> IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.38f),
        )
    }
    IconButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = size.minimumSize, minHeight = size.minimumSize).cursorForHand(),
        enabled = enabled,
        colors = colors,
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(size.iconSize))
    }
}

@Composable
fun KomeliaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val motion = LocalKomeliaMotion.current
    val containerColor by animateColorAsState(
        targetValue = if (isHovered && enabled) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(motion.duration(motion.stateDurationMillis), easing = motion.standardEasing),
        label = "cardContainerColor",
    )
    Card(
        onClick = onClick,
        modifier = modifier.cursorForHand(),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun KomeliaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KomeliaSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (supportingText != null) {
                Spacer(Modifier.height(KomeliaSpacing.extraSmall))
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.small)) {
        SectionHeader(title = title, supportingText = supportingText)
        KomeliaCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(KomeliaSpacing.large),
                verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.medium),
                content = content,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    imageVector: ImageVector? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(modifier = modifier, color = Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (imageVector != null) {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.height(KomeliaSpacing.medium))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (description != null) {
                Spacer(Modifier.height(KomeliaSpacing.small))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(KomeliaSpacing.large))
                action()
            }
        }
    }
}
