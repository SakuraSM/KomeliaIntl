package snd.komelia.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_mobile_subtitle
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_mobile_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.appRootNavigator
import snd.komelia.ui.common.components.BrowsePageHeader
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.settings.navigation.SettingsNavigationMenu

class MobileSettingsScreen(
    val topLevel: Boolean = false,
) : Screen {
    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel {
            viewModelFactory.getSettingsNavigationViewModel(currentNavigator.appRootNavigator())
        }
        val layout = LocalKomeliaLayout.current
        LaunchedEffect(Unit) { vm.initialize() }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            KomeliaPageScaffold(topBar = {
                BrowsePageHeader(
                    title = stringResource(Res.string.settings_mobile_title),
                    navigationIcon = if (topLevel) null else {
                        {
                            IconButton(onClick = { currentNavigator.pop() }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
                            }
                        }
                    },
                )

            }) { topInset ->
                Box(Modifier.fillMaxSize().padding(top = topInset + layout.controlSpacing)) {
                    SettingsNavigationMenu(
                        header = {
                            if (topLevel) Column(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(stringResource(Res.string.settings_mobile_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        currentScreen = currentNavigator.lastItem,
                        onNavigation = { currentNavigator.push(it) },
                        hasMediaErrors = vm.hasMediaErrors,
                        komfEnabled = vm.komfEnabledFlow.collectAsState().value,
                        updatesEnabled = vm.updatesEnabled,
                        newVersionIsAvailable = vm.newVersionIsAvailable,
                        onLogout = vm::logout,
                        user = vm.user.collectAsState().value,
                        contentColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = layout.pageHorizontalPadding)
                    )
                }
            }
        }
        if (!topLevel) BackPressHandler { currentNavigator.pop() }
    }
}
