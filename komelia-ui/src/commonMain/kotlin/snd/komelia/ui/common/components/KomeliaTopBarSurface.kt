package snd.komelia.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalKomeliaMotion

private val LocalHeaderMaterialProvided = staticCompositionLocalOf { false }

/** Paints one header material through the status area and consumes its safe inset once. */
@Composable
fun KomeliaSafeTopBar(content: @Composable () -> Unit) {
    if (LocalHeaderMaterialProvided.current) {
        content()
        return
    }
    val topInset = LocalPageTopInset.current
    KomeliaChromeSurface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxWidth().padding(top = topInset)) {
            CompositionLocalProvider(
                LocalPageTopInset provides 0.dp,
                LocalHeaderMaterialProvided provides true,
                content = content,
            )
        }
    }
}

internal data class KomeliaTopBarElevation(
    val tonal: Dp,
    val shadow: Dp,
)

internal fun komeliaTopBarElevation(isContentScrolled: Boolean, useGlass: Boolean = false): KomeliaTopBarElevation =
    if (isContentScrolled && !useGlass) {
        KomeliaTopBarElevation(tonal = 1.dp, shadow = 2.dp)
    } else {
        KomeliaTopBarElevation(tonal = 0.dp, shadow = 0.dp)
    }

@Stable
class KomeliaTopBarScrollState internal constructor() {
    var isContentScrolled by mutableStateOf(false)
        private set

    internal val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            when {
                consumed.y < 0f -> isContentScrolled = true
                available.y > 0f -> isContentScrolled = false
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0f) isContentScrolled = false
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberKomeliaTopBarScrollState(): KomeliaTopBarScrollState =
    remember { KomeliaTopBarScrollState() }

fun Modifier.komeliaTopBarScroll(state: KomeliaTopBarScrollState): Modifier =
    nestedScroll(state.nestedScrollConnection)

@Composable
fun KomeliaTopBarSurface(
    isContentScrolled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = LocalKomeliaMotion.current
    val targetElevation = komeliaTopBarElevation(isContentScrolled, LocalSurfaceAppearance.current.useGlass)
    val animationSpec = tween<Dp>(
        durationMillis = motion.duration(motion.pressDurationMillis),
        easing = motion.standardEasing,
    )
    val tonalElevation by animateDpAsState(
        targetValue = targetElevation.tonal,
        animationSpec = animationSpec,
        label = "topBarTonalElevation",
    )
    val shadowElevation by animateDpAsState(
        targetValue = targetElevation.shadow,
        animationSpec = animationSpec,
        label = "topBarShadowElevation",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isContentScrolled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(
            durationMillis = motion.duration(motion.pressDurationMillis),
            easing = motion.standardEasing,
        ),
        label = "topBarContainerColor",
    )

    KomeliaChromeSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}
