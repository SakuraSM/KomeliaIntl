package snd.komelia.ui.common.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import snd.komelia.settings.model.AppSurfaceStyle

/** Native menu behavior stays with Material; only the window material and corners change. */
@Composable
fun KomeliaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassStyle = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = if (glassStyle) modifier.komeliaChrome(KomeliaChromeStyle.menuShape) else modifier,
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = KomeliaChromeStyle.menuShape,
        containerColor = if (glassStyle) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (glassStyle) 0.dp else 8.dp,
    ) {
        KomeliaChromeContent { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.KomeliaExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    matchAnchorWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassStyle = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = if (glassStyle) modifier.komeliaChrome(KomeliaChromeStyle.menuShape) else modifier,
        scrollState = scrollState,
        matchAnchorWidth = matchAnchorWidth,
        shape = KomeliaChromeStyle.menuShape,
        containerColor = if (glassStyle) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (glassStyle) 0.dp else 8.dp,
    ) {
        KomeliaChromeContent { content() }
    }
}
