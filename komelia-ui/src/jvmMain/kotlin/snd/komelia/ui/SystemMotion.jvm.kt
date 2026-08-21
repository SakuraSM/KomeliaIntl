package snd.komelia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit

@Composable
actual fun rememberSystemReducedMotion(): Boolean = remember {
    val toolkitPreference = runCatching {
        Toolkit.getDefaultToolkit().getDesktopProperty("awt.reduceMotion") as? Boolean
    }.getOrNull()
    toolkitPreference ?: System.getProperty("apple.awt.reduceMotion")?.toBooleanStrictOrNull() ?: false
}
