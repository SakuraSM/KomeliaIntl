package snd.komelia.ui.settings.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_announcements
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_about
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_app_settings
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_appearance
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_home_groups
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_epub_reader
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_image_reader
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_komf_connection
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_komf_jobs
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_komf_notifications
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_komf_processing
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_komf_providers
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_komf_settings
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_log_out
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_log_out_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_my_account
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_my_auth_activity
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_offline_mode
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_server_auth_activity
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_server_general
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_server_media_management
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_server_settings
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_server_users
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_updates
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_user_settings
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_title
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalOfflineAvailable
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.home.edit.HomeGroupsSettingsScreen
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.account.AccountSettingsScreen
import snd.komelia.ui.settings.about.AboutSettingsScreen
import snd.komelia.ui.settings.analysis.MediaAnalysisScreen
import snd.komelia.ui.settings.announcements.AnnouncementsScreen
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import snd.komelia.ui.settings.authactivity.AuthenticationActivityScreen
import snd.komelia.ui.settings.epub.EpubReaderSettingsScreen
import snd.komelia.ui.settings.imagereader.ImageReaderSettingsScreen
import snd.komelia.ui.settings.komf.general.KomfSettingsScreen
import snd.komelia.ui.settings.komf.jobs.KomfJobsScreen
import snd.komelia.ui.settings.komf.notifications.KomfNotificationSettingsScreen
import snd.komelia.ui.settings.komf.processing.KomfProcessingSettingsScreen
import snd.komelia.ui.settings.komf.providers.KomfProvidersSettingsScreen
import snd.komelia.ui.settings.network.NetworkSettingsScreen
import snd.komelia.ui.settings.offline.OfflineSettingsScreen
import snd.komelia.ui.settings.server.ServerSettingsScreen
import snd.komelia.ui.settings.updates.AppUpdatesScreen
import snd.komelia.ui.settings.users.UsersScreen
import snd.komf.api.MediaServer.KOMGA
import snd.komga.client.user.KomgaUser
import snd.webview.webviewIsAvailable

@Composable
fun SettingsNavigationMenu(
    hasMediaErrors: Boolean,
    komfEnabled: Boolean,
    updatesEnabled: Boolean,
    newVersionIsAvailable: Boolean,
    currentScreen: Screen,
    onNavigation: (Screen) -> Unit = {},
    onLogout: () -> Unit,
    user: KomgaUser?,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val isAdmin = remember(user) { user?.roleAdmin() ?: true }
    val offlineAvailable = LocalOfflineAvailable.current
    val isOffline = LocalOfflineMode.current.collectAsState().value
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    val appEntries = buildList {
        add(
            NavigationEntry(
                stringResource(Res.string.settings_navigation_appearance),
                Icons.Rounded.Palette,
                AppSettingsScreen(),
                currentScreen is AppSettingsScreen,
            )
        )
        add(
            NavigationEntry(
                stringResource(Res.string.settings_navigation_home_groups),
                Icons.Rounded.Tune,
                HomeGroupsSettingsScreen(),
                currentScreen is HomeGroupsSettingsScreen,
            )
        )
        add(
            NavigationEntry(
                stringResource(Res.string.settings_network_title),
                Icons.Rounded.Lan,
                NetworkSettingsScreen(),
                currentScreen is NetworkSettingsScreen,
            )
        )
        add(
            NavigationEntry(
                stringResource(Res.string.settings_navigation_image_reader),
                Icons.Rounded.Image,
                ImageReaderSettingsScreen(),
                currentScreen is ImageReaderSettingsScreen,
            )
        )
        if (webviewIsAvailable()) {
            add(
                NavigationEntry(
                    stringResource(Res.string.settings_navigation_epub_reader),
                    Icons.AutoMirrored.Rounded.MenuBook,
                    EpubReaderSettingsScreen(),
                    currentScreen is EpubReaderSettingsScreen,
                )
            )
        }
        if (updatesEnabled) {
            add(
                NavigationEntry(
                    stringResource(Res.string.settings_navigation_updates),
                    null,
                    AppUpdatesScreen(),
                    currentScreen is AppUpdatesScreen,
                    error = newVersionIsAvailable,
                )
            )
        }
        if (offlineAvailable) {
            add(
                NavigationEntry(
                    stringResource(Res.string.settings_navigation_offline_mode),
                    Icons.Rounded.CloudDownload,
                    OfflineSettingsScreen(),
                    currentScreen is OfflineSettingsScreen,
                )
            )
        }
        add(
            NavigationEntry(
                stringResource(Res.string.settings_navigation_about),
                Icons.Rounded.Info,
                AboutSettingsScreen(),
                currentScreen is AboutSettingsScreen,
            )
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsNavigationSection(
            title = stringResource(Res.string.settings_navigation_app_settings),
            entries = appEntries,
            contentColor = contentColor,
            onNavigation = onNavigation,
        )

        if (!isOffline) {
            SettingsNavigationSection(
                title = stringResource(Res.string.settings_navigation_user_settings),
                entries = listOf(
                    NavigationEntry(
                        stringResource(Res.string.settings_navigation_my_account),
                        Icons.Rounded.AccountCircle,
                        AccountSettingsScreen(),
                        currentScreen is AccountSettingsScreen,
                    ),
                    NavigationEntry(
                        stringResource(Res.string.settings_navigation_my_auth_activity),
                        Icons.Rounded.History,
                        AuthenticationActivityScreen(true),
                        currentScreen is AuthenticationActivityScreen && currentScreen.forMe,
                    ),
                ),
                contentColor = contentColor,
                onNavigation = onNavigation,
            )
            if (isAdmin) {
                SettingsNavigationSection(
                    title = stringResource(Res.string.settings_navigation_server_settings),
                    entries = listOf(
                        NavigationEntry(stringResource(Res.string.settings_navigation_server_general), null, ServerSettingsScreen(), currentScreen is ServerSettingsScreen),
                        NavigationEntry(stringResource(Res.string.settings_navigation_server_users), null, UsersScreen(), currentScreen is UsersScreen),
                        NavigationEntry(
                            stringResource(Res.string.settings_navigation_server_auth_activity),
                            null,
                            AuthenticationActivityScreen(false),
                            currentScreen is AuthenticationActivityScreen && !currentScreen.forMe,
                        ),
                        NavigationEntry(
                            stringResource(Res.string.settings_navigation_server_media_management),
                            null,
                            MediaAnalysisScreen(),
                            currentScreen is MediaAnalysisScreen,
                            error = hasMediaErrors,
                        ),
                        NavigationEntry(stringResource(Res.string.settings_navigation_announcements), null, AnnouncementsScreen(), currentScreen is AnnouncementsScreen),
                    ),
                    contentColor = contentColor,
                    onNavigation = onNavigation,
                )
            }

            if (isAdmin) {
                val komfEntries = buildList {
                    add(NavigationEntry(stringResource(Res.string.settings_navigation_komf_connection), null, KomfSettingsScreen(), currentScreen is KomfSettingsScreen))
                    if (komfEnabled) {
                        add(NavigationEntry(stringResource(Res.string.settings_navigation_komf_processing), null, KomfProcessingSettingsScreen(KOMGA), currentScreen is KomfProcessingSettingsScreen))
                        add(NavigationEntry(stringResource(Res.string.settings_navigation_komf_providers), null, KomfProvidersSettingsScreen(), currentScreen is KomfProvidersSettingsScreen))
                        add(NavigationEntry(stringResource(Res.string.settings_navigation_komf_notifications), null, KomfNotificationSettingsScreen(), currentScreen is KomfNotificationSettingsScreen))
                        add(NavigationEntry(stringResource(Res.string.settings_navigation_komf_jobs), null, KomfJobsScreen(), currentScreen is KomfJobsScreen))
                    }
                }
                SettingsNavigationSection(
                    title = stringResource(Res.string.settings_navigation_komf_settings),
                    entries = komfEntries,
                    contentColor = contentColor,
                    onNavigation = onNavigation,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            NavigationButton(
                label = stringResource(Res.string.settings_navigation_log_out),
                onClick = { showLogoutConfirmation = true },
                isSelected = false,
                color = contentColor,
            )
        }
        if (showLogoutConfirmation) {
            ConfirmationDialog(
                title = stringResource(Res.string.settings_navigation_log_out),
                body = stringResource(Res.string.settings_navigation_log_out_confirm),
                buttonConfirm = stringResource(Res.string.settings_navigation_log_out),
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,

                onDialogConfirm = onLogout,
                onDialogDismiss = { showLogoutConfirmation = false })
        }
    }
}

private data class NavigationEntry(
    val label: String,
    val icon: ImageVector?,
    val screen: Screen,
    val selected: Boolean,
    val error: Boolean = false,
)

@Composable
private fun SettingsNavigationSection(
    title: String,
    entries: List<NavigationEntry>,
    contentColor: Color,
    onNavigation: (Screen) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                entries.forEachIndexed { index, entry ->
                    NavigationButton(
                        label = entry.label,
                        icon = entry.icon,
                        onClick = { onNavigation(entry.screen) },
                        isSelected = entry.selected,
                        error = entry.error,
                        color = contentColor,
                        showDivider = index < entries.lastIndex,
                    )
                }
            }
        }
    }
}


@Composable
fun NavigationButton(
    label: String,
    icon: ImageVector? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    warn: Boolean = false,
    error: Boolean = false,
    color: Color,
    showDivider: Boolean = false,
) {
    val platform = LocalPlatform.current
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        platform == MOBILE -> Color.Transparent
        else -> color
    }

    val height = when (platform) {
        MOBILE -> 48.dp
        DESKTOP, WEB_KOMF -> 40.dp
    }

    Surface(
        onClick = { if (!isSelected) onClick() },
        shape = if (isSelected) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp),
        color = containerColor,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .cursorForHand()
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = label,
                style = if (platform == MOBILE) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(5.dp))
            if (error) {
                val color = MaterialTheme.colorScheme.error
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = color)
                }
            } else if (warn) {
                val color = MaterialTheme.colorScheme.tertiary
                Canvas(modifier = Modifier.size(30.dp)) {
                    drawCircle(color = color)
                }
            }
            Spacer(Modifier.weight(1f))
            if (platform == MOBILE) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = if (platform == MOBILE) 46.dp else 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }

}
