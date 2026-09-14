package snd.komelia.ui.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import snd.komelia.ui.dialogs.permissions.AccessLocalNetworkRequestDialog
import snd.komelia.ui.dialogs.ConfirmationDialog
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_android_lan_access_dialog
import snd.komelia.ui.platform.hasLanPermission
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_title
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.MainScreen
import snd.komelia.ui.appRootNavigator
import snd.komelia.ui.login.offline.OfflineLoginScreen
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.settings.SettingsScreenContainer

class LoginScreen : Screen {

    @Composable
    override fun Content() {
        val rootNavigator = LocalNavigator.currentOrThrow.appRootNavigator()
        val platform = LocalPlatform.current
        val viewModelFactory = LocalViewModelFactory.current
        val isOffline = LocalOfflineMode.current
        val vm = rememberScreenModel(isOffline.value.toString()) { viewModelFactory.getLoginViewModel() }

        LaunchedEffect(Unit) { vm.initialize() }
        Column {
            PlatformTitleBar { }
            when (platform) {
                MOBILE, DESKTOP ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) { ScreenContent(vm, rootNavigator) }

                WEB_KOMF -> SettingsScreenContainer(title = stringResource(Res.string.login_title)) {
                    ScreenContent(vm, rootNavigator)
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
    }

    @Composable
    private fun ScreenContent(
        viewModel: LoginViewModel,
        rootNavigator: Navigator
    ) {
        val state = viewModel.state.collectAsState()
        val platform = LocalPlatform.current
        val granted = hasLanPermission()
        var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var requestSystemPermission by remember { mutableStateOf(false) }
        var autoPermissionOffered by remember { mutableStateOf(false) }
        LaunchedEffect(state.value, viewModel.autoLoginError, granted) {
            if (state.value is Error && viewModel.autoLoginError != null &&
                !granted && !autoPermissionOffered) {
                autoPermissionOffered = true
                permissionAction = viewModel::retryAutoLogin
            }
        }
        val withPermission: (() -> Unit) -> Unit = { action ->
            if (granted) action() else permissionAction = action
        }
        if (permissionAction != null && !requestSystemPermission) {
            ConfirmationDialog(
                body = stringResource(Res.string.login_android_lan_access_dialog),
                onDialogConfirm = { requestSystemPermission = true },
                onDialogDismiss = {
                    // ConfirmationDialog also dismisses after Confirm. Keep the pending
                    // action until the system permission callback in that case.
                    if (!requestSystemPermission) {
                        val action = permissionAction
                        permissionAction = null
                        action?.invoke()
                    }
                },
            )
        }
        if (permissionAction != null && requestSystemPermission) {
            AccessLocalNetworkRequestDialog {
                val action = permissionAction
                permissionAction = null
                requestSystemPermission = false
                // A denied LAN grant must not prevent remote-server or offline use.
                action?.invoke()
            }
        }

        when (state.value) {
            Loading, Uninitialized -> LoginLoadingContent(viewModel::cancel)

            is Error -> if (platform == WEB_KOMF) KomfLoginContent(
                url = viewModel.url,
                onUrlChange = viewModel::onUrlChange,
                apiKey = viewModel.apiKey,
                onApiKeyChange = { viewModel.apiKey = it },
                userLoginError = viewModel.userLoginError,
                autoLoginError = viewModel.autoLoginError,
                onAutoLoginRetry = viewModel::retryAutoLogin,
                onLogin = viewModel::loginWithApiKey,
            ) else LoginContent(
                url = viewModel.url,
                onUrlChange = viewModel::onUrlChange,
                user = viewModel.user,
                onUserChange = { viewModel.user = it },
                password = viewModel.password,
                onPasswordChange = { viewModel.password = it },
                userLoginError = viewModel.userLoginError,
                serverUrlError = viewModel.serverUrlError,
                autoLoginError = viewModel.autoLoginError,
                onAutoLoginRetry = { withPermission(viewModel::retryAutoLogin) },
                onLogin = { withPermission(viewModel::loginWithCredentials) },
                offlineIsAvailable = viewModel.offlineIsAvailable.collectAsState().value,
                onOfflineSelect = { rootNavigator.replaceAll(OfflineLoginScreen()) },
                canGoOfflineAsCurrentUser = viewModel.canGoOfflineAsCurrentUser.collectAsState(false).value,
                goOfflineAsCurrentUser = viewModel::offlineLogin,
                localLibraryIsAvailable = viewModel.localLibraryIsAvailable,
                onLocalLibrarySelect = viewModel::localLibraryLogin,
            )

            is Success -> rootNavigator.replaceAll(MainScreen())
        }

    }
}
