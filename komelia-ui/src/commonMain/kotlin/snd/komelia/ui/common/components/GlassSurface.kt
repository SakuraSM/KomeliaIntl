@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class)

package snd.komelia.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LocalKomeliaMotion

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
val LocalPageTopInset = staticCompositionLocalOf { 0.dp }
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

/** Scrolling and active editing pause capture; chrome keeps its last available backdrop. */
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
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val appearance = LocalSurfaceAppearance.current
    val state = LocalGlassBackdrop.current
    val colors = MaterialTheme.colorScheme
    val baseColor = chromeBaseColor(appearance, colors, color)
    val glass = appearance.useGlass && state != null
    Surface(
        modifier = modifier.komeliaHighlight(highlighted, shape, interactionSource = interactionSource),
        shape = shape,
        color = if (glass) Color.Transparent else baseColor,
        contentColor = colors.onSurface,
        tonalElevation = if (glass) 0.dp else tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        val effect = if (appearance.style == AppSurfaceStyle.LIQUID_GLASS) {
            Modifier.komeliaChrome(shape, baseColor, interactionSource)
        } else Modifier
        Box(effect) { KomeliaChromeContent(content) }
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
    val baseColor = chromeBaseColor(appearance, colors, color)
    if (!appearance.useGlass || state == null) {
        val solid = if (appearance.style == AppSurfaceStyle.LIQUID_GLASS) baseColor.copy(alpha = 1f) else baseColor
        return background(solid, shape)
    }
    val material = remember(appearance.theme, baseColor, shape) {
        // Override the library's optical edge and white-point defaults for uniform frosted chrome.
        GlassStyle.regular.then {
            shape(shape)
            optics(blurRadius = KomeliaChromeStyle.blurRadius, refractionStrength = 0f, refractionDetailIntensity = 0f)
            backgroundColor(baseColor)
            tint(baseColor.copy(alpha = KomeliaChromeStyle.tintOpacity))
            specularIntensity(0f)
            edgeShadow(Color.Transparent)
            // Do not blend the sharp source back into the last pixel band of frosted shapes.
            edgeSoftness(0.dp)
            ambientResponse(0f)
            contrast(0f)
            whitePoint(0f)
            chromaMultiplier(1.1f)
            contentNormalBlend(0f)
        }
    }
    return clip(shape).hazeGlass(
        input = HazeInput.Sources(state, retention = HazeSourceRetention.KeepLastFrame),
        style = material,
        performanceMode = HazePerformanceMode.Balanced,
        interactionSource = interactionSource,
        interactionReducedMotionPolicy = if (motion.isReducedMotion) GlassReducedMotionPolicy.Reduced else GlassReducedMotionPolicy.System,
    )
}

private fun chromeBaseColor(appearance: SurfaceAppearance, colors: ColorScheme, requested: Color): Color =
    if (appearance.style == AppSurfaceStyle.LIQUID_GLASS) glassTintColor(appearance.theme, colors) else requested
