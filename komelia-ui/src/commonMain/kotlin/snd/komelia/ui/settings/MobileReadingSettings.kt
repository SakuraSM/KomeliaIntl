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
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.strings.AppStrings
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
    val appearance = LocalSurfaceAppearance.current
    val appearanceSummary = stringResource(AppStrings.forAppTheme(appearance.theme)) + " · " + stringResource(
        if (appearance.style == AppSurfaceStyle.LIQUID_GLASS) Res.string.appearance_liquid_glass else Res.string.appearance_classic)
    Column(modifier.verticalScroll(rememberScrollState())
        .padding(bottom = LocalNavigationBarInset.current + 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReadingSettingsGroup {
            SettingsGroupLabel(stringResource(Res.string.reading_settings_group))
            if (textReaderAvailable) ReadingSettingsRow(stringResource(Res.string.reading_text_settings), Icons.AutoMirrored.Rounded.MenuBook,
                stringResource(Res.string.reading_text_settings_summary)) { onNavigation(EpubReaderSettingsScreen()) }
            ReadingSettingsRow(stringResource(Res.string.reading_image_settings), Icons.Rounded.Image,
                stringResource(Res.string.reading_image_settings_summary)) { onNavigation(ImageReaderSettingsScreen()) }
            ReadingSettingsRow(stringResource(Res.string.settings_navigation_appearance), Icons.Rounded.Palette, appearanceSummary) { onNavigation(AppSettingsScreen()) }
        }
        if (offlineAvailable) ReadingSettingsGroup {
            SettingsGroupLabel(stringResource(Res.string.reading_offline_group))
            ReadingSettingsRow(stringResource(Res.string.settings_navigation_local_library), Icons.Rounded.Folder,
                stringResource(Res.string.reading_local_summary)) { onNavigation(LocalLibrarySettingsScreen()) }
            ReadingSettingsRow(stringResource(Res.string.reading_downloads_cache), Icons.Rounded.CloudDownload,
                stringResource(Res.string.reading_downloads_summary)) { onNavigation(OfflineSettingsScreen()) }
        }
        ReadingSettingsGroup {
            SettingsGroupLabel(stringResource(Res.string.reading_library_group))
            ReadingSettingsRow(stringResource(Res.string.reading_account_connections), Icons.Rounded.Link,
                stringResource(Res.string.reading_account_summary)) { onNavigation(MobileSettingsSectionScreen(MobileSettingsSection.CONNECTIONS)) }
            ReadingSettingsRow(stringResource(Res.string.reading_advanced), Icons.Rounded.Settings,
                stringResource(Res.string.reading_advanced_summary)) { onNavigation(MobileSettingsSectionScreen(MobileSettingsSection.ADVANCED)) }
        }
        ReadingSettingsRow(stringResource(Res.string.reading_about_updates), Icons.Rounded.Info) { onNavigation(MobileSettingsSectionScreen(MobileSettingsSection.ABOUT)) }
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 9.dp))
}

@Composable
private fun ReadingSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 6.dp, bottom = 8.5.dp), content = content)
    }
}

@Composable
private fun ReadingSettingsRow(title: String, icon: ImageVector, description: String? = null, onClick: () -> Unit) {
    Column {
        Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
            Row(Modifier.fillMaxWidth().heightIn(min = 65.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
                    if (description != null) Text(description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
