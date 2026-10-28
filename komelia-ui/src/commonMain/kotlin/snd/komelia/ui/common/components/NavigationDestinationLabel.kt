package snd.komelia.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import snd.komelia.settings.model.AppSurfaceStyle

/** The marker keeps selection recognizable over both plain and illustrated glass backdrops. */
@Composable
internal fun NavigationDestinationLabel(label: String, isSelected: Boolean) {
    val floating = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
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
                    .background(if (isSelected) LocalContentColor.current else Color.Transparent, CircleShape),
            )
        }
    }
}
