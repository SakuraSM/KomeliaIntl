package snd.komelia.ui.settings.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_network_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.collectAsState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.settings.SettingsScreenContainer

class NetworkSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val viewModel = rememberScreenModel { viewModelFactory.getNetworkSettingsViewModel() }
        LaunchedEffect(Unit) { viewModel.initialize() }

        SettingsScreenContainer(stringResource(Res.string.settings_network_title)) {
            NetworkSettingsContent(
                lanServerUrl = viewModel.lanServerUrl,
                lanServerUrlError = viewModel.lanServerUrlError,
                lanAutoSwitchEnabled = viewModel.lanAutoSwitchEnabled,
                connectionStatus = viewModel.serverUrlResolver.connectionStatus.collectAsState().value,
                effectiveServerUrl = viewModel.serverUrlResolver.effectiveServerUrl.collectAsState().value,
                onLanServerUrlChange = viewModel::onLanServerUrlChange,
                onLanAutoSwitchEnabledChange = viewModel::onLanAutoSwitchEnabledChange,
                onCheckLanConnection = viewModel::checkLanConnection,
            )
        }
    }
}
