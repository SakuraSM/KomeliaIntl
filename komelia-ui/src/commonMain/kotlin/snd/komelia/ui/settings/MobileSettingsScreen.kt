package snd.komelia.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_mobile_title
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.settings.navigation.SettingsNavigationMenu

class MobileSettingsScreen(
    val topLevel: Boolean = false,
) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getSettingsNavigationViewModel(currentNavigator) }
        val layout = LocalKomeliaLayout.current
        LaunchedEffect(Unit) { vm.initialize() }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = layout.pageHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = layout.pageVerticalPadding),
                    horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!topLevel) {
                        IconButton(onClick = { currentNavigator.pop() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, null)
                        }
                    }
                    Text(
                        stringResource(Res.string.settings_mobile_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                SettingsNavigationMenu(
                    currentScreen = currentNavigator.lastItem,
                    onNavigation = { currentNavigator.push(it) },
                    hasMediaErrors = vm.hasMediaErrors,
                    komfEnabled = vm.komfEnabledFlow.collectAsState().value,
                    updatesEnabled = vm.updatesEnabled,
                    newVersionIsAvailable = vm.newVersionIsAvailable,
                    onLogout = vm::logout,
                    user = vm.user.collectAsState().value,
                    contentColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (!topLevel) BackPressHandler { currentNavigator.pop() }
    }
}
