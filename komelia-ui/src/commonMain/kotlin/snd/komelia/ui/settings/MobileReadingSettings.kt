package snd.komelia.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.appRootNavigator
import snd.komelia.ui.common.components.BrowsePageHeader
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.LocalNavigationBarInset
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import snd.komelia.ui.settings.epub.EpubReaderSettingsScreen
import snd.komelia.ui.settings.imagereader.ImageReaderSettingsScreen
import snd.komelia.ui.settings.local.LocalLibrarySettingsScreen
import snd.komelia.ui.settings.navigation.SettingsNavigationMenu
import snd.komelia.ui.settings.offline.OfflineSettingsScreen

internal enum class MobileSettingsSection { CONNECTIONS, ADVANCED, ABOUT }

internal class MobileSettingsSectionScreen(private val section: MobileSettingsSection) : Screen {
    @Composable override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val factory = LocalViewModelFactory.current
        val vm = rememberScreenModel { factory.getSettingsNavigationViewModel(navigator.appRootNavigator()) }
        LaunchedEffect(Unit) { vm.initialize() }
        val title = stringResource(when (section) {
            MobileSettingsSection.CONNECTIONS -> Res.string.reading_account_connections
            MobileSettingsSection.ADVANCED -> Res.string.reading_advanced
            MobileSettingsSection.ABOUT -> Res.string.reading_about_updates
        })
        KomeliaPageScaffold(topBar = {
            BrowsePageHeader(title, navigationIcon = {
                IconButton(onClick = { navigator.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back)) }
            })
        }) { topInset ->
            SettingsNavigationMenu(currentScreen = navigator.lastItem, onNavigation = { navigator.push(it) },
                hasMediaErrors = vm.hasMediaErrors, komfEnabled = vm.komfEnabledFlow.collectAsState().value,
                updatesEnabled = vm.updatesEnabled, newVersionIsAvailable = vm.newVersionIsAvailable,
                onLogout = vm::logout, user = vm.user.collectAsState().value,
                contentColor = MaterialTheme.colorScheme.surface, mobileSection = section,
                modifier = Modifier.fillMaxSize().padding(top = topInset + 16.dp).padding(horizontal = 16.dp))
        }
        BackPressHandler { navigator.pop() }
    }
}

@Composable
internal fun MobileReadingSettingsHome(
    onNavigation: (Screen) -> Unit,
    offlineAvailable: Boolean,
    textReaderAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState())
        .padding(bottom = LocalNavigationBarInset.current + 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            SettingsGroupLabel(stringResource(Res.string.reading_settings_group))
            if (textReaderAvailable) ReadingSettingsRow(stringResource(Res.string.reading_text_settings), Icons.AutoMirrored.Rounded.MenuBook,
                stringResource(Res.string.reading_text_settings_summary)) { onNavigation(EpubReaderSettingsScreen()) }
            ReadingSettingsRow(stringResource(Res.string.reading_image_settings), Icons.Rounded.Image,
                stringResource(Res.string.reading_image_settings_summary)) { onNavigation(ImageReaderSettingsScreen()) }
            ReadingSettingsRow(stringResource(Res.string.settings_navigation_appearance), Icons.Rounded.Palette) { onNavigation(AppSettingsScreen()) }
        }
        if (offlineAvailable) Column {
            SettingsGroupLabel(stringResource(Res.string.reading_offline_group))
            ReadingSettingsRow(stringResource(Res.string.settings_navigation_local_library), Icons.Rounded.Folder) { onNavigation(LocalLibrarySettingsScreen()) }
            ReadingSettingsRow(stringResource(Res.string.reading_downloads_cache), Icons.Rounded.CloudDownload) { onNavigation(OfflineSettingsScreen()) }
        }
        Column {
            SettingsGroupLabel(stringResource(Res.string.reading_library_group))
            ReadingSettingsRow(stringResource(Res.string.reading_account_connections), Icons.Rounded.AccountCircle) { onNavigation(MobileSettingsSectionScreen(MobileSettingsSection.CONNECTIONS)) }
            ReadingSettingsRow(stringResource(Res.string.reading_advanced), Icons.Rounded.Settings,
                stringResource(Res.string.reading_advanced_summary)) { onNavigation(MobileSettingsSectionScreen(MobileSettingsSection.ADVANCED)) }
        }
        ReadingSettingsRow(stringResource(Res.string.reading_about_updates), Icons.Rounded.Info) { onNavigation(MobileSettingsSectionScreen(MobileSettingsSection.ABOUT)) }
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun ReadingSettingsRow(title: String, icon: ImageVector, description: String? = null, onClick: () -> Unit) {
    Column {
        Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
            Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (description != null) Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
