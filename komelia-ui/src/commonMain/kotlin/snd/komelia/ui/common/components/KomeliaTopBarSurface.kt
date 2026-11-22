package snd.komelia.ui.common.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.LocalKomeliaMotion

private enum class HeaderFrame { None, Plain, Frosted }

private val LocalHeaderFrame = staticCompositionLocalOf { HeaderFrame.None }
internal val LocalFrostedPageHeaders = staticCompositionLocalOf { false }

/** Consumes the cutout once and keeps an absent toolbar at zero height. */
@Composable
fun KomeliaSafeTopBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalHeaderFrame.current != HeaderFrame.None) {
        content()
        return
    }
    val frosted = LocalFrostedPageHeaders.current && LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    val topInset = LocalPageTopInset.current
    // Apply the material to the measured status inset as well as the toolbar row.
    val material = if (frosted) Modifier.komeliaChrome(RoundedCornerShape(0.dp)) else Modifier
    Layout(modifier = modifier.fillMaxWidth().then(material), content = {
        Box(if (frosted) Modifier else Modifier.background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(
                LocalPageTopInset provides 0.dp,
                LocalHeaderFrame provides if (frosted) HeaderFrame.Frosted else HeaderFrame.Plain,
            ) {
                Layout(content = content) { measurables, constraints ->
                    val children = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
                    val contentHeight = children.maxOfOrNull { it.height } ?: 0
                    val height = contentHeight
                    layout(constraints.maxWidth, height) { children.forEach { it.placeRelative(0, 0) } }
                }
            }
        }
    }) { measurables, constraints ->
        val safeTop = topInset.roundToPx()
        val header = measurables.single().measure(
            constraints.offset(vertical = -safeTop).copy(minHeight = 0),
        )
        val height = if (header.height == 0) 0 else header.height + safeTop
        layout(constraints.maxWidth, height) { if (height > 0) header.placeRelative(0, safeTop) }
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
    // The surrounding safe header already draws the material across both rows.
    if (LocalHeaderFrame.current == HeaderFrame.Frosted) {
        Box(modifier.fillMaxWidth()) { KomeliaChromeContent(content) }
        return
    }
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
