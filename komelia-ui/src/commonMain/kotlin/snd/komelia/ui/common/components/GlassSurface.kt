@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package snd.komelia.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LocalKomeliaMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable
data class SurfaceAppearance(
    val style: AppSurfaceStyle = AppSurfaceStyle.CLASSIC,
    val theme: AppTheme = AppTheme.DARK,
    val reduceTransparency: Boolean = false,
    val renderingSupported: Boolean = true,
) {
    val useGlass: Boolean get() = style == AppSurfaceStyle.LIQUID_GLASS && !reduceTransparency && renderingSupported
}

val LocalSurfaceAppearance = staticCompositionLocalOf { SurfaceAppearance() }
val LocalNavigationBarInset = staticCompositionLocalOf { 0.dp }
private val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }
internal val LocalGlassSamplingBudget = staticCompositionLocalOf<GlassSamplingBudget?> { null }

internal class GlassSamplingBudget(private val scope: CoroutineScope) {
    private val activeOwners = mutableSetOf<Any>()
    private var resume: Job? = null
    var isPaused by mutableStateOf(false)
        private set

    fun begin(owner: Any) {
        resume?.cancel()
        activeOwners.add(owner)
        isPaused = true
    }

    fun end(owner: Any) {
        if (!activeOwners.remove(owner) || activeOwners.isNotEmpty()) return
        resume?.cancel()
        resume = scope.launch {
            // Consecutive drags/flings share one pause instead of reallocating layers per gesture.
            delay(250)
            isPaused = false
        }
    }
}

/** Scrolling and active editing use solid chrome; idle controls restore the selected material. */
@Composable
fun PauseGlassWhileBusy(isBusy: Boolean) {
    val budget = LocalGlassSamplingBudget.current
    DisposableEffect(budget, isBusy) {
        val owner = Any()
        if (isBusy) budget?.begin(owner)
        onDispose { budget?.end(owner) }
    }
}

/** Each scope captures content only; its chrome must be a sibling of that source. */
@Composable
fun GlassBackdropScope(content: @Composable () -> Unit) {
    val state = remember { HazeState() }
    val inheritedBudget = LocalGlassSamplingBudget.current
    val scope = rememberCoroutineScope()
    val budget = inheritedBudget ?: remember(scope) { GlassSamplingBudget(scope) }
    CompositionLocalProvider(LocalGlassBackdrop provides state, LocalGlassSamplingBudget provides budget, content = content)
}

/** Disabled styles attach no capture modifier. Removing the source releases its owned layer. */
@Composable
fun Modifier.glassBackdrop(active: Boolean = true): Modifier {
    val state = LocalGlassBackdrop.current
    val paused = LocalGlassSamplingBudget.current?.isPaused == true
    return if (active && !paused && LocalSurfaceAppearance.current.useGlass && state != null) hazeSource(state) else this
}

/** Cross-window dialogs deliberately use their existing solid Surface, never this sampler. */
@Composable
fun KomeliaChromeSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    interactionSource: InteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val appearance = LocalSurfaceAppearance.current
    val state = LocalGlassBackdrop.current
    val colors = MaterialTheme.colorScheme
    val glass = appearance.useGlass && state != null && LocalGlassSamplingBudget.current?.isPaused != true
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = colors.onSurface,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = shadowElevation,
        border = if (appearance.style == AppSurfaceStyle.LIQUID_GLASS) BorderStroke(1.dp, colors.outlineVariant) else null,
    ) {
        val effect = if (glass) Modifier.komeliaChrome(shape, color, interactionSource) else Modifier
        Box(effect) { content() }
    }
}

/** Material-only decoration for existing rows and controls; it installs no gestures. */
@Composable
fun Modifier.komeliaChrome(
    shape: RoundedCornerShape,
    color: Color = MaterialTheme.colorScheme.surface,
    interactionSource: InteractionSource? = null,
): Modifier {
    val appearance = LocalSurfaceAppearance.current
    val state = LocalGlassBackdrop.current
    val motion = LocalKomeliaMotion.current
    val colors = MaterialTheme.colorScheme
    if (!appearance.useGlass || state == null || LocalGlassSamplingBudget.current?.isPaused == true) {
        val solid = if (appearance.style == AppSurfaceStyle.LIQUID_GLASS) color.copy(alpha = 1f) else color
        return background(solid, shape)
    }
    val material = remember(appearance.theme, colors.surface, shape) {
        // Override every appearance-dependent Regular channel; app and OS themes can differ.
        val dark = appearance.theme != AppTheme.LIGHT
        val tone = glassTone(appearance.theme)
        GlassStyle.regular.then {
            shape(shape)
            backgroundColor(colors.surface)
            tint(colors.surface.copy(alpha = tone.opacity))
            specularIntensity(if (dark) 0.38f else 0.48f)
            edgeShadow(Color.Black.copy(alpha = if (dark) 0.32f else 0.14f))
            ambientResponse(if (dark) 0.08f else 0.15f)
            contrast(tone.contrast)
            whitePoint(tone.whitePoint)
            chromaMultiplier(if (dark) 1.1f else 1.5f)
        }
    }
    return background(color, shape).clip(shape).hazeGlass(
        input = HazeInput.Sources(state, retention = HazeSourceRetention.ClearWhenUnavailable),
        style = material,
        performanceMode = HazePerformanceMode.Balanced,
        interactionSource = interactionSource,
        interactionReducedMotionPolicy = if (motion.isReducedMotion) GlassReducedMotionPolicy.Reduced else GlassReducedMotionPolicy.System,
    )
}

internal data class GlassTone(val whitePoint: Float, val contrast: Float, val opacity: Float)

// Regular's theme response, selected from the app preference rather than the OS appearance.
internal fun glassTone(theme: AppTheme): GlassTone = if (theme == AppTheme.LIGHT) {
    GlassTone(whitePoint = 0.55f, contrast = 0f, opacity = 0.78f)
} else {
    GlassTone(whitePoint = -0.22f, contrast = 0.08f, opacity = 0.72f)
}
