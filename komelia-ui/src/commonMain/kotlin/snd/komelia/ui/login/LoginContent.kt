package snd.komelia.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component1
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component2
import androidx.compose.ui.focus.FocusRequester.Companion.FocusRequesterFactory.component3
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_cancel
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_go_offline
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_komf_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_komf_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_login
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_invalid_port
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_invalid_url
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_offline_mode
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_password
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_retry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_subtitle
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_url
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_username
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_with_another_account
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.KomeliaSpacing
import snd.komelia.ui.common.components.OutlinedHttpTextField
import snd.komelia.ui.common.components.withTextFieldNavigation
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.cursorForHand


@Composable
fun LoginContent(
    url: String,
    onUrlChange: (String) -> Unit,
    user: String,
    onUserChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    userLoginError: String?,
    serverUrlError: LoginServerUrlError?,
    autoLoginError: String?,
    onAutoLoginRetry: () -> Unit,
    onLogin: () -> Unit,
    offlineIsAvailable: Boolean,
    onOfflineSelect: () -> Unit,
    canGoOfflineAsCurrentUser: Boolean,
    goOfflineAsCurrentUser: () -> Unit,
) {

    val layout = LocalKomeliaLayout.current
    var showAutoLoginError by remember { mutableStateOf(true) }
    val serverUrlErrorMessage = when (serverUrlError) {
        LoginServerUrlError.INVALID_URL -> stringResource(Res.string.login_invalid_url)
        LoginServerUrlError.INVALID_PORT -> stringResource(Res.string.login_invalid_port)
        null -> null
    }
    if (autoLoginError != null && showAutoLoginError) {
        Column(
            verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                autoLoginError,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            ) {
                Button(onClick = {
                    showAutoLoginError = false
                }) { Text(stringResource(Res.string.login_with_another_account)) }
                if (canGoOfflineAsCurrentUser) {
                    Button(onClick = goOfflineAsCurrentUser) { Text(stringResource(Res.string.login_go_offline)) }
                }

                Button(onClick = onAutoLoginRetry) { Text(stringResource(Res.string.login_retry)) }
            }
        }
    } else {
        val platform = LocalPlatform.current
        when (platform) {
            MOBILE, DESKTOP -> Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(layout.cardContentPadding),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.large),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(KomeliaSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(KomeliaSpacing.medium),
                            )
                        }
                        Column {
                            Text("Komelia", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                stringResource(Res.string.login_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    LoginForm(
                        url = url,
                        onUrlChange = onUrlChange,
                        user = user,
                        onUserChange = onUserChange,
                        password = password,
                        onPasswordChange = onPasswordChange,
                        errorMessage = serverUrlErrorMessage ?: userLoginError,
                        onLogin = onLogin,
                        offlineIsAvailable = offlineIsAvailable,
                        onOfflineSelect = onOfflineSelect,
                        textFieldsModifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            PlatformType.WEB_KOMF -> Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing)
            ) {
                val uriHandler = LocalUriHandler.current
                Column {
                    Text(stringResource(Res.string.login_komf_title))
                    Text(
                        stringResource(Res.string.login_komf_desc),
                        color = MaterialTheme.colorScheme.secondary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://komga.org/docs/installation/configuration/#komga_cors_allowed_origins--komgacorsallowed-origins-origins")
                        }.padding(2.dp).cursorForHand()
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoginForm(
                        url = url,
                        onUrlChange = onUrlChange,
                        user = user,
                        onUserChange = onUserChange,
                        password = password,
                        onPasswordChange = onPasswordChange,
                        errorMessage = serverUrlErrorMessage ?: userLoginError,
                        onLogin = onLogin,
                        offlineIsAvailable = offlineIsAvailable,
                        onOfflineSelect = onOfflineSelect,
                        textFieldsModifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

    }

}

@Composable
fun ColumnScope.LoginForm(
    url: String,
    onUrlChange: (String) -> Unit,
    user: String,
    onUserChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    errorMessage: String?,
    onLogin: () -> Unit,
    offlineIsAvailable: Boolean,
    onOfflineSelect: () -> Unit,
    textFieldsModifier: Modifier
) {

    val coroutineScope = rememberCoroutineScope()
    val (first, second, third) = remember { FocusRequester.createRefs() }

    OutlinedHttpTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(Res.string.login_url)) },
        modifier = textFieldsModifier
            .withTextFieldNavigation()
            .focusRequester(first)
            .focusProperties { next = second },
        placeholder = { Text("localhost:25600") },
        singleLine = true,
    )

    OutlinedTextField(
        value = user,
        onValueChange = onUserChange,
        label = { Text(stringResource(Res.string.login_username)) },
        modifier = textFieldsModifier
            .withTextFieldNavigation()
            .focusRequester(second)
            .focusProperties { next = third },
        singleLine = true,
    )

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        visualTransformation = PasswordVisualTransformation(),
        label = { Text(stringResource(Res.string.login_password)) },
        modifier = textFieldsModifier
            .withTextFieldNavigation(
                onEnterPress = { coroutineScope.launch { onLogin() } }
            )
            .focusRequester(third),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
    )

    if (errorMessage != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(KomeliaSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(KomeliaSpacing.small),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(KomeliaSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (offlineIsAvailable) {
            TextButton(
                onClick = onOfflineSelect,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.login_offline_mode)) }
        }
        Button(
            onClick = { onLogin() },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text(stringResource(Res.string.login_login)) }
    }
}

@Composable
fun LoginLoadingContent(onCancel: () -> Unit) {
    var showCancelButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5000)
        showCancelButton = true
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()
        if (showCancelButton) {
            Spacer(Modifier.height(100.dp))
            Button(onClick = onCancel) { Text(stringResource(Res.string.login_cancel)) }
        }

    }
}
