package snd.komelia.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_apikey
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_komf_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_komf_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_login
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.login_url
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.common.components.OutlinedHttpTextField
import snd.komelia.ui.common.components.withTextFieldNavigation
import snd.komelia.ui.platform.cursorForHand

@Composable
fun KomfLoginContent(
    url: String,
    onUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    userLoginError: String?,
    autoLoginError: String?,
    onAutoLoginRetry: () -> Unit,
    onLogin: () -> Unit,
) {
    var showAutoLoginError by remember { mutableStateOf(true) }
    if (autoLoginError != null && showAutoLoginError) {
        Column {
            Text(autoLoginError, color = MaterialTheme.colorScheme.error)
            Button(onClick = onAutoLoginRetry) { Text(stringResource(Res.string.login_login)) }
            Button(onClick = { showAutoLoginError = false }) { Text(stringResource(Res.string.login_apikey)) }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val uriHandler = LocalUriHandler.current
            Column {
                Text(stringResource(Res.string.login_komf_title))
                Text(
                    stringResource(Res.string.login_komf_desc),
                    color = MaterialTheme.colorScheme.secondary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://komga.org/docs/installation/configuration/#sample-configuration-file")
                    }.padding(2.dp).cursorForHand()
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KomfLoginForm(
                    url = url,
                    onUrlChange = onUrlChange,
                    apiKey = apiKey,
                    onApiKeyChange = onApiKeyChange,
                    errorMessage = userLoginError,
                    onLogin = onLogin,
                )
            }
        }

    }
}

@Composable
fun ColumnScope.KomfLoginForm(
    url: String,
    onUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    errorMessage: String?,
    onLogin: () -> Unit,
) {
    val (first, second, third) = remember { FocusRequester.createRefs() }

    OutlinedHttpTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(Res.string.login_url)) },
        modifier = Modifier
            .fillMaxWidth()
            .withTextFieldNavigation()
            .focusRequester(first)
            .focusProperties { next = second },
    )

    OutlinedTextField(
        value = apiKey,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        onValueChange = onApiKeyChange,
        label = { Text(stringResource(Res.string.login_apikey)) },
        modifier = Modifier
            .fillMaxWidth()
            .withTextFieldNavigation()
            .focusRequester(second)
            .focusProperties { next = third }
    )

    if (errorMessage != null) {
        Text(errorMessage, style = TextStyle(color = MaterialTheme.colorScheme.error))
    }

    Button(onClick = { onLogin() }) {
        Text(stringResource(Res.string.login_login))
    }
    Spacer(Modifier.imePadding())
}
