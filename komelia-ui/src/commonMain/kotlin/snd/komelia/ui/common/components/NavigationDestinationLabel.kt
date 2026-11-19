package snd.komelia.ui.common.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.LocalKomeliaMotion

/** The marker keeps selection recognizable over both plain and illustrated glass backdrops. */
@Composable
internal fun NavigationDestinationLabel(label: String, isSelected: Boolean) {
    val floating = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    val motion = LocalKomeliaMotion.current
    val targetAlpha = if (isSelected) 1f else 0f
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(motion.duration(motion.stateDurationMillis), easing = motion.standardEasing),
        label = "navigationMarker",
    )
    val markerAlpha = if (motion.isReducedMotion) targetAlpha else animatedAlpha
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (floating && isSelected) FontWeight.Bold else null,
        )
        if (floating) {
            Box(
                Modifier.padding(top = KomeliaChromeStyle.navigationMarkerSpacing)
                    .size(KomeliaChromeStyle.navigationMarkerWidth, KomeliaChromeStyle.navigationMarkerHeight)
                    .testTag("navigation-selection-indicator")
                    .background(LocalContentColor.current.copy(alpha = LocalContentColor.current.alpha * markerAlpha), CircleShape),
            )
        }
    }
}
