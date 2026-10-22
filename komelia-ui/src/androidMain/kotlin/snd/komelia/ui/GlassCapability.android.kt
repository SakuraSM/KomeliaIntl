package snd.komelia.ui

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberGlassRenderingSupported(): Boolean {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val manager = remember(context) { context.getSystemService(ActivityManager::class.java) }
    var memoryPressure by remember(context) { mutableStateOf(false) }
    DisposableEffect(context) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { memoryPressure = true }
            override fun onTrimMemory(level: Int) {
                if (level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) memoryPressure = true
            }
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
    return Build.VERSION.SDK_INT >= 33 && view.isHardwareAccelerated && !manager.isLowRamDevice && !memoryPressure
}
