package snd.komelia.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ENTERING_CONTENT_ALPHA = 0.92f

/** Updates content immediately and animates only the incoming layer. */
@Composable
internal fun SingleLayerPageTransition(
    targetKey: String,
    modifier: Modifier = Modifier,
    enterOffset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val motion = LocalKomeliaMotion.current
    val offsetPx = with(LocalDensity.current) { enterOffset.toPx() }
    val alpha = remember { Animatable(1f) }
    val offset = remember { Animatable(0f) }
    var previousKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(targetKey, motion.isReducedMotion) {
        val action = screenTransitionAction(previousKey, targetKey)
        previousKey = targetKey
        if (action != ScreenTransitionAction.AnimateChange || motion.isReducedMotion) {
            alpha.snapTo(1f)
            offset.snapTo(0f)
        } else {
            alpha.snapTo(ENTERING_CONTENT_ALPHA)
            offset.snapTo(offsetPx)
            val duration = motion.duration(motion.contentDurationMillis)
            coroutineScope {
                launch { alpha.animateTo(1f, tween(duration, easing = motion.standardEasing)) }
                if (offsetPx != 0f) launch { offset.animateTo(0f, tween(duration, easing = motion.standardEasing)) }
            }
        }
    }
    Box(modifier.fillMaxSize().graphicsLayer {
        this.alpha = alpha.value
        translationX = offset.value
    }) { content() }
}
