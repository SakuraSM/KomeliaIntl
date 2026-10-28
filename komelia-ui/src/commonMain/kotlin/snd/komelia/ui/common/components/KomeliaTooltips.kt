package snd.komelia.ui.common.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snd.komelia.settings.model.AppSurfaceStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipScope.KomeliaPlainTooltip(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 320.dp,
    content: @Composable () -> Unit,
) {
    val glassStyle = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    PlainTooltip(
        modifier = if (glassStyle) modifier.komeliaChrome(KomeliaChromeStyle.menuShape) else modifier,
        shape = KomeliaChromeStyle.menuShape,
        maxWidth = maxWidth,
        containerColor = if (glassStyle) Color.Transparent else MaterialTheme.colorScheme.inverseSurface,
        contentColor = if (glassStyle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        KomeliaChromeContent(content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipScope.KomeliaRichTooltip(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    maxWidth: Dp = 320.dp,
    content: @Composable () -> Unit,
) {
    val glassStyle = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    val colors = MaterialTheme.colorScheme
    RichTooltip(
        modifier = if (glassStyle) modifier.komeliaChrome(KomeliaChromeStyle.menuShape) else modifier,
        title = title,
        shape = KomeliaChromeStyle.menuShape,
        maxWidth = maxWidth,
        colors = if (glassStyle) TooltipDefaults.richTooltipColors(
            containerColor = Color.Transparent,
            contentColor = colors.onSurface,
            titleContentColor = colors.onSurface,
            actionContentColor = colors.onSurface,
        ) else TooltipDefaults.richTooltipColors(),
    ) {
        KomeliaChromeContent(content)
    }
}
