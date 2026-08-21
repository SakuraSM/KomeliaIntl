package snd.komelia.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun animationsAreDisabled(): Boolean =
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f

    var isReducedMotion by remember(resolver) { mutableStateOf(animationsAreDisabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isReducedMotion = animationsAreDisabled()
            }
        }
        resolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return isReducedMotion
}
