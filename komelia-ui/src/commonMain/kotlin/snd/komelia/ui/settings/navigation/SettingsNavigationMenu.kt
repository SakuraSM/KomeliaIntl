package snd.komelia.ui.settings.navigation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalStrings
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.account.AccountSettingsScreen
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
    val strings = LocalStrings.current.settingsNavigation
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        val isOffline = LocalOfflineMode.current.collectAsState().value
        Text(strings.appSettings, style = MaterialTheme.typography.titleSmall)
        NavigationButton(
            label = strings.appearance,
            onClick = { onNavigation(AppSettingsScreen()) },
            isSelected = currentScreen is AppSettingsScreen,
            color = contentColor,
        )
        NavigationButton(
            label = LocalStrings.current.legacy.forText("Network Connection"),
            onClick = { onNavigation(NetworkSettingsScreen()) },
            isSelected = currentScreen is NetworkSettingsScreen,
            color = contentColor,
        )
        NavigationButton(
            label = strings.imageReader,
            onClick = { onNavigation(ImageReaderSettingsScreen()) },
            isSelected = currentScreen is ImageReaderSettingsScreen,
            color = contentColor,
        )
        if (webviewIsAvailable()) {
            NavigationButton(
                label = strings.epubReader,
                onClick = { onNavigation(EpubReaderSettingsScreen()) },
                isSelected = currentScreen is EpubReaderSettingsScreen,
                color = contentColor,
            )
        }
        if (updatesEnabled) {
            NavigationButton(
                label = strings.updates,
                onClick = { onNavigation(AppUpdatesScreen()) },
                isSelected = currentScreen is AppUpdatesScreen,
                error = newVersionIsAvailable,
                color = contentColor,
            )
        }
        NavigationButton(
            label = strings.offlineMode,
            onClick = { onNavigation(OfflineSettingsScreen()) },
            isSelected = currentScreen is OfflineSettingsScreen,
            color = contentColor,
        )

        HorizontalDivider(Modifier.padding(vertical = 10.dp))


        if (!isOffline) {
            Text(strings.userSettings, style = MaterialTheme.typography.titleSmall)
            NavigationButton(
                label = strings.myAccount,
                onClick = { onNavigation(AccountSettingsScreen()) },
                isSelected = currentScreen is AccountSettingsScreen,
                color = contentColor,
            )

            NavigationButton(
                label = strings.myAuthenticationActivity,
                onClick = { onNavigation(AuthenticationActivityScreen(true)) },
                isSelected = currentScreen is AuthenticationActivityScreen && currentScreen.forMe,
                color = contentColor,
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (isAdmin) {
                Text(strings.serverSettings, style = MaterialTheme.typography.titleSmall)
                NavigationButton(
                    label = strings.general,
                    onClick = { onNavigation(ServerSettingsScreen()) },
                    isSelected = currentScreen is ServerSettingsScreen,
                    color = contentColor,
                )

                NavigationButton(
                    label = strings.users,
                    onClick = { onNavigation(UsersScreen()) },
                    isSelected = currentScreen is UsersScreen,
                    color = contentColor,
                )
                NavigationButton(
                    label = strings.authenticationActivity,
                    onClick = { onNavigation(AuthenticationActivityScreen(false)) },
                    isSelected = currentScreen is AuthenticationActivityScreen && !currentScreen.forMe,
                    color = contentColor,
                )
                if (hasMediaErrors) {
                    NavigationButton(
                        label = strings.mediaManagement,
                        onClick = { onNavigation(MediaAnalysisScreen()) },
                        isSelected = currentScreen is MediaAnalysisScreen,
                        error = true,
                        color = contentColor,
                    )
                }

                NavigationButton(
                    label = strings.announcements,
                    onClick = { onNavigation(AnnouncementsScreen()) },
                    isSelected = currentScreen is AnnouncementsScreen,
                    color = contentColor,
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
            }

            if (isAdmin) {
                Text(strings.komfSettings, style = MaterialTheme.typography.titleSmall)
                NavigationButton(
                    label = strings.connection,
                    onClick = { onNavigation(KomfSettingsScreen()) },
                    isSelected = currentScreen is KomfSettingsScreen,
                    color = contentColor,
                )
                AnimatedVisibility(komfEnabled) {
                    Column {
                        NavigationButton(
                            label = strings.processing,
                            onClick = { onNavigation(KomfProcessingSettingsScreen(KOMGA)) },
                            isSelected = currentScreen is KomfProcessingSettingsScreen,
                            color = contentColor,
                        )
                        NavigationButton(
                            label = strings.providers,
                            onClick = { onNavigation(KomfProvidersSettingsScreen()) },
                            isSelected = currentScreen is KomfProvidersSettingsScreen,
                            color = contentColor,
                        )
                        NavigationButton(
                            label = strings.notifications,
                            onClick = { onNavigation(KomfNotificationSettingsScreen()) },
                            isSelected = currentScreen is KomfNotificationSettingsScreen,
                            color = contentColor,
                        )
                        NavigationButton(
                            label = strings.jobHistory,
                            onClick = { onNavigation(KomfJobsScreen()) },
                            isSelected = currentScreen is KomfJobsScreen,
                            color = contentColor,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
            }
        }

        var showLogoutConfirmation by remember { mutableStateOf(false) }
        NavigationButton(
            label = strings.logOut,
            onClick = { showLogoutConfirmation = true },
            isSelected = false,
            color = contentColor,
        )
        if (showLogoutConfirmation) {
            ConfirmationDialog(
                title = strings.logOutConfirmTitle,
                body = strings.logOutConfirmBody,
                buttonConfirm = strings.logOut,
                buttonConfirmColor = MaterialTheme.colorScheme.errorContainer,

                onDialogConfirm = onLogout,
                onDialogDismiss = { showLogoutConfirmation = false })
        }
    }
}


@Composable
fun NavigationButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    warn: Boolean = false,
    error: Boolean = false,
    color: Color
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceContainer else color

    val height = when (LocalPlatform.current) {
        MOBILE -> 50.dp
        DESKTOP, WEB_KOMF -> 40.dp
    }

    Surface(
        onClick = { if (!isSelected) onClick() },
        shape = RoundedCornerShape(3.dp),
        color = containerColor,
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
            Text(label, style = MaterialTheme.typography.titleMedium)
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
        }
    }

}
