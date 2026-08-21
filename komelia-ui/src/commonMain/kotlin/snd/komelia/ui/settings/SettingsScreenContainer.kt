package snd.komelia.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.KomeliaSpacing
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformTitleBar
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
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        PlatformTitleBar()
        Row(
            horizontalArrangement = Arrangement.spacedBy(KomeliaSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navigator.pop() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }

            Text(title, style = MaterialTheme.typography.titleLarge)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier.weight(1f, false).imePadding().verticalScroll(rememberScrollState())
                .padding(KomeliaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.large),
        ) {
            content()
        }

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
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
    Column(Modifier.widthIn(min = 0.dp, max = settingsDesktopContentWidth)) {
        Spacer(Modifier.height(KomeliaSpacing.huge))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = KomeliaSpacing.huge),
        )
        Spacer(Modifier.height(KomeliaSpacing.extraLarge))

        Column(
            modifier = Modifier.padding(horizontal = KomeliaSpacing.huge, vertical = KomeliaSpacing.large),
            verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.large),
            content = content
        )
    }
}
