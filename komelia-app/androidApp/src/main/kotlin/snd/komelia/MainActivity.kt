package snd.komelia

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.window.layout.WindowMetricsCalculator
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import io.github.snd_r.komelia.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import snd.komelia.ui.MainView
import snd.komelia.ui.DependencyContainer
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

private val initScope = CoroutineScope(Dispatchers.Default)
private val initMutex = Mutex()
private val mainActivity = MutableStateFlow<MainActivity?>(null)

internal suspend fun initializeDependencies(context: Context): DependencyContainer =
    initMutex.withLock {
        dependencies.value ?: AndroidAppModule(
            context = context.applicationContext,
            mainActivity = mainActivity,
        ).initDependencies().also { dependencies.value = it }
    }

class MainActivity : AppCompatActivity() {
    private companion object {
        // Activity creation runs on the main thread. Returning from the background
        // or recreating the Activity for locale/rotation must not replay the intro.
        var hasCreatedActivity = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(R.color.launch_background)
        val coldLauncherStart = !hasCreatedActivity && savedInstanceState == null &&
            intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        hasCreatedActivity = true
        FileKit.init(this)
        mainActivity.value = this

        initScope.launch { initializeDependencies(applicationContext) }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        setContent {
            val windowSize = rememberWindowSize()
            val keyEvents = remember { MutableSharedFlow<KeyEvent>() }
            val appDependencies by dependencies.collectAsState()
            var showLaunch by remember { mutableStateOf(coldLauncherStart) }
            var firstScreenReady by remember { mutableStateOf(false) }
            val onFirstScreenReady: (() -> Unit)? = if (coldLauncherStart) {
                remember { { firstScreenReady = true } }
            } else null
            Box(Modifier.fillMaxSize()) {
                if (!showLaunch || appDependencies != null) {
                    MainView(
                        dependencies = appDependencies,
                        windowWidth = WindowSizeClass.fromDp(windowSize.width),
                        windowHeight = WindowSizeClass.fromDp(windowSize.height),
                        platformType = PlatformType.MOBILE,
                        keyEvents = keyEvents,
                        appLocaleController = AndroidAppLocaleController,
                        onFirstScreenReady = if (firstScreenReady) null else onFirstScreenReady,
                    )
                }
                if (showLaunch) LaunchMotion(firstScreenReady) { showLaunch = false }
            }
        }
    }
}

@Composable
private fun Activity.rememberWindowSize(): DpSize {
    val configuration = LocalConfiguration.current
    val windowMetrics = remember(configuration) {
        WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(this)
    }
    val windowDpSize = with(LocalDensity.current) {
        windowMetrics.bounds.toComposeRect().size.toDpSize()
    }
    return windowDpSize
}
