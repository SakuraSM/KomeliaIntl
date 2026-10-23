package snd.komelia.ui.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType
import kotlin.test.assertTrue

class LoginContentLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun desktopLoginKeepsReadableFormWidth() {
        renderLogin(PlatformType.DESKTOP, 1280.dp)
        val bounds = compose.onNode(hasSetTextAction() and hasText("reader@example.invalid"))
            .getUnclippedBoundsInRoot()
        val formWidth = bounds.right - bounds.left
        assertTrue(formWidth <= 440.dp, "Desktop login must respect its 440dp card limit, got $formWidth")
    }

    @Test fun compactLoginUsesAvailableWidth() {
        renderLogin(PlatformType.MOBILE, 360.dp)
        val bounds = compose.onNode(hasSetTextAction() and hasText("reader@example.invalid"))
            .getUnclippedBoundsInRoot()
        val formWidth = bounds.right - bounds.left
        assertTrue(formWidth in 300.dp..360.dp, "Compact login must use the available width, got $formWidth")
    }

    private fun renderLogin(platform: PlatformType, width: Dp) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalPlatform provides platform) {
                    Box(Modifier.requiredSize(width, 800.dp), contentAlignment = Alignment.Center) {
                        LoginContent(
                            url = "http://localhost:25600", onUrlChange = {},
                            user = "reader@example.invalid", onUserChange = {},
                            password = "", onPasswordChange = {},
                            userLoginError = null, serverUrlError = null, autoLoginError = null,
                            onAutoLoginRetry = {}, onLogin = {},
                            offlineIsAvailable = false, onOfflineSelect = {},
                            canGoOfflineAsCurrentUser = false, goOfflineAsCurrentUser = {},
                            localLibraryIsAvailable = true, onLocalLibrarySelect = {},
                        )
                    }
                }
            }
        }
    }
}
