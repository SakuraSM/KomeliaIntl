package snd.komelia.ui.komf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.tween
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.common.components.GlassBackdropScope
import snd.komelia.ui.common.components.glassBackdrop
import snd.komelia.ui.common.components.komeliaChrome
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.CrossfadeTransition
import snd.komelia.ui.platform.PlatformTitleBar
import snd.komelia.ui.settings.SettingsScreenLayout
import snd.komelia.ui.settings.komf.general.KomfSettingsScreen
import snd.komelia.ui.settings.settingsDesktopNavMenuWidth
import snd.komelia.ui.settings.settingsDesktopTopPadding

class KomfMainScreen : Screen {

    @Composable
    override fun Content() {
        Navigator(
            screen = KomfSettingsScreen(integrationToggleEnabled = false, showKavitaSettings = true),
            onBackPressed = null
        ) { navigator ->
            val motion = LocalKomeliaMotion.current
            GlassBackdropScope {
                Column {
                    PlatformTitleBar()
                    SettingsScreenLayout(
                        navMenu = {
                            Row(
                                Modifier
                                    .komeliaChrome(RoundedCornerShape(0.dp), MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(top = settingsDesktopTopPadding, start = 10.dp, end = 10.dp)
                            ) {
                                Spacer(Modifier.weight(1f))
                                KomfNavigationContent(
                                    currentScreen = navigator.lastItem,
                                    onNavigation = { navigator.replaceAll(it) },
                                    contentColor = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.width(settingsDesktopNavMenuWidth)
                                )
                            }
                        },
                        dismissButton = {},
                        content = { Box(Modifier.fillMaxSize().glassBackdrop()) { CrossfadeTransition(navigator, animationSpec = tween(motion.duration(motion.contentDurationMillis))) } },
                    )
                }
            }
        }
    }
}
