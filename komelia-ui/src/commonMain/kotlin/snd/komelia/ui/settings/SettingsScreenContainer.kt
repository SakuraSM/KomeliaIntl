package snd.komelia.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.components.KomeliaTopBarSurface
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.VerticalScrollbar

@Composable
fun SettingsScreenContainer(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val platform = LocalPlatform.current
    when (platform) {
        MOBILE -> MobileContainer(title, content)
        DESKTOP, WEB_KOMF -> DesktopContainer(title, content)
    }
}

@Composable
private fun MobileContainer(title: String, content: @Composable ColumnScope.() -> Unit) {
    val navigator = LocalNavigator.currentOrThrow
    val layout = LocalKomeliaLayout.current
    val scrollState = rememberScrollState()
    val isContentScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        KomeliaTopBarSurface(isContentScrolled = isContentScrolled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontalPadding)
                    .padding(vertical = layout.controlSpacing),
                horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigator.pop() }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(Res.string.navigation_back)
                    )
                }

                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = layout.pageHorizontalPadding)
                .padding(
                    top = layout.topBarContentSpacing,
                    bottom = layout.pageVerticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
        ) {
            content()
        }
    }
    BackPressHandler { navigator.pop() }
}

@Composable
private fun DesktopContainer(title: String, content: @Composable ColumnScope.() -> Unit) {
    val scrollState = rememberScrollState()
    Box(Modifier.background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            DesktopContent(title, content)
        }
        VerticalScrollbar(scrollState, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun DesktopContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    val layout = LocalKomeliaLayout.current
    Column(Modifier.widthIn(min = 0.dp, max = settingsDesktopContentWidth)) {
        Spacer(Modifier.height(layout.pageVerticalPadding))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = layout.pageHorizontalPadding),
        )
        Spacer(Modifier.height(layout.sectionSpacing))

        Column(
            modifier = Modifier.padding(
                horizontal = layout.pageHorizontalPadding,
                vertical = layout.pageVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
            content = content
        )
    }
}
