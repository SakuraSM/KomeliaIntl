package snd.komelia.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_mobile_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.common.components.KomeliaChromeSurface
import snd.komelia.ui.platform.BackPressHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt

@Composable
fun SettingsScreenLayout(
    navMenu: @Composable () -> Unit,
    content: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    val latestNavMenu = rememberUpdatedState(navMenu)
    // Keep focus and popup owners alive when the navigation changes at the breakpoint.
    val retainedContent = remember { movableContentOf { latestContent.value() } }
    val retainedNavMenu = remember { movableContentOf { latestNavMenu.value() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 840.dp) CompactSettingsLayout(retainedNavMenu, retainedContent, dismissButton)
        else DesktopSettingsLayout(retainedNavMenu, retainedContent, dismissButton)
    }
}

@Composable
private fun CompactSettingsLayout(
    navMenu: @Composable () -> Unit,
    content: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenKey = LocalNavigator.current?.lastItem?.key
    LaunchedEffect(screenKey) { drawer.close() }
    if (drawer.isOpen) BackPressHandler { scope.launch { drawer.close() } }
    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        modifier = Modifier.onPreviewKeyEvent {
            if (drawer.isOpen && it.type == KeyEventType.KeyUp && it.key == Key.Escape) {
                scope.launch { drawer.close() }; true
            } else false
        },
        drawerContent = {
            ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) { navMenu() }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            KomeliaChromeSurface(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawer.open() } }) {
                        Icon(Icons.Rounded.Menu, contentDescription = stringResource(Res.string.settings_mobile_title))
                    }
                    Text(stringResource(Res.string.settings_mobile_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    dismissButton()
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun DesktopSettingsLayout(
    navMenu: @Composable () -> Unit,
    content: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) = Layout(
    modifier = Modifier.fillMaxSize(),
    contents = listOf(navMenu, content, dismissButton)
) { (navMenuMeasurable, contentMeasurable, dismissMeasurable), constraints ->
    val navWidth = settingsDesktopNavMenuWidth.roundToPx()
    val contentWidth = settingsDesktopContentWidth.roundToPx()
    val padding =
        ((constraints.maxWidth - (navWidth + contentWidth)).toFloat() / 2).roundToInt().coerceAtLeast(0)

    val contentPlaceable = contentMeasurable.first()
        .measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = padding + contentWidth.coerceAtMost(constraints.maxWidth - navWidth)
            )
        )

    val navMenuPlaceable = navMenuMeasurable.first()
        .measure(
            constraints.copy(
                minWidth = 0,
                maxWidth = padding + navWidth
            )
        )
    val dismissPlaceable = dismissMeasurable.firstOrNull()?.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        navMenuPlaceable.placeRelative(
            0,
            0
        )
        contentPlaceable.placeRelative(
            padding + navWidth,
            0
        )
        dismissPlaceable?.placeRelative(
            (padding + navWidth + contentWidth).coerceAtMost(constraints.maxWidth - dismissPlaceable.width),
            0
        )
    }
}
