package snd.komelia.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_close_settings
import org.jetbrains.compose.resources.stringResource
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.transitions.CrossfadeTransition
import kotlinx.coroutines.flow.SharedFlow
import snd.komelia.ui.LocalKeyEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.appearance.AppSettingsScreen
import snd.komelia.ui.settings.navigation.SettingsNavigationMenu

val settingsDesktopNavMenuWidth = 264.dp
val settingsDesktopContentWidth = 840.dp
val settingsDesktopTopPadding = 32.dp

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val keyEvents: SharedFlow<KeyEvent> = LocalKeyEvents.current
        val vm = rememberScreenModel { viewModelFactory.getSettingsNavigationViewModel(currentNavigator) }

        LaunchedEffect(Unit) { vm.initialize() }
        LaunchedEffect(Unit) { keyEvents.collect { if (it.key == Key.Escape) currentNavigator.pop() } }

        Navigator(
            screen = AppSettingsScreen(),
            onBackPressed = null
        ) { navigator ->
            Column {
                PlatformTitleBar()
                SettingsScreenLayout(
                    navMenu = {
                        Row(
                            Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(top = settingsDesktopTopPadding, start = 16.dp, end = 16.dp)
                        ) {
                            Spacer(Modifier.weight(1f))
                            SettingsNavigationMenu(
                                currentScreen = navigator.lastItem,
                                onNavigation = { navigator.replaceAll(it) },
                                hasMediaErrors = vm.hasMediaErrors,
                                komfEnabled = vm.komfEnabledFlow.collectAsState().value,
                                updatesEnabled = vm.updatesEnabled,
                                newVersionIsAvailable = vm.newVersionIsAvailable,
                                onLogout = vm::logout,
                                contentColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.width(settingsDesktopNavMenuWidth),
                                user = vm.user.collectAsState().value
                            )
                        }
                    },
                    dismissButton = {
                        OutlinedIconButton(
                            onClick = { currentNavigator.pop() },
                            modifier = Modifier.cursorForHand().padding(top = settingsDesktopTopPadding),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            content = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(Res.string.navigation_close_settings),
                                )
                            }
                        )
                    },
                    content = { CrossfadeTransition(navigator) },
                )

            }
        }
        BackPressHandler { currentNavigator.pop() }

    }

}
