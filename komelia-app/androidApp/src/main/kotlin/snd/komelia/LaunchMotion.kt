package snd.komelia

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.R
import snd.komelia.ui.rememberSystemReducedMotion

private const val ConvergedAt = 180
private const val MarkRevealedAt = 420
private const val ExitDuration = 230
private const val MarkViewport = 135.46666f
private val Ice = Color(0xFF70D9FF)
private val MarkWhite = Color(0xFFF0F8FF)

@Composable
internal fun LaunchMotion(firstScreenReady: Boolean, onFinished: () -> Unit) {
    val reducedMotion = rememberSystemReducedMotion()
    val elapsed = remember { Animatable(if (reducedMotion) MarkRevealedAt.toFloat() else 0f) }
    val fade = remember { Animatable(0f) }
    var introFinished by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            elapsed.snapTo(MarkRevealedAt.toFloat())
        } else if (!introFinished) {
            elapsed.animateTo(MarkRevealedAt.toFloat(), tween(MarkRevealedAt, easing = LinearEasing))
        }
        introFinished = true
    }
    LaunchedEffect(firstScreenReady, introFinished, reducedMotion) {
        if (firstScreenReady && (introFinished || reducedMotion)) {
            if (!reducedMotion) fade.animateTo(1f, tween(ExitDuration, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    val pathData = stringResource(R.string.komelia_mark_path)
    val mark = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = 1f - fade.value
            }
            .background(colorResource(R.color.launch_background))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val time = elapsed.value
            drawSignalTrails(time)
            drawLaunchMark(mark, time)
        }
        Text(
            text = "Komelia",
            color = MarkWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.align(Alignment.Center).offset(y = 64.dp)
                .clearAndSetSemantics { }
                .graphicsLayer { alpha = easedPhase(elapsed.value, 245, MarkRevealedAt) },
        )
        if (introFinished && !firstScreenReady) {
            Text(
                text = stringResource(R.string.launch_starting),
                color = Color(0xFFABC4D5),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).offset(y = 97.dp),
            )
        }
    }
}

// Each signal travels toward a different point on the stationary mark.
private val SignalOrigins = listOf(
    Offset(-0.87f, -0.38f), Offset(-0.95f, -0.07f), Offset(-0.88f, 0.43f),
    Offset(0.85f, -0.41f), Offset(0.95f, 0.09f), Offset(0.89f, 0.46f),
)
private val SignalTargets = listOf(
    Offset(-0.06f, -0.18f), Offset(-0.22f, 0.09f), Offset(-0.12f, 0.12f),
    Offset(0.24f, -0.16f), Offset(0.04f, 0.02f), Offset(0.09f, 0.26f),
)

private fun DrawScope.drawSignalTrails(time: Float) {
    val radius = minOf(size.width * 0.47f, 160.dp.toPx())
    val convergence = easedPhase(time, 0, ConvergedAt)
    val trailStrength = (0.06f + 0.94f * convergence) * (1f - easedPhase(time, ConvergedAt, 360))
    val gridStep = 11.dp.toPx()
    for (index in -6..6) {
        val offset = gridStep * index
        val extent = gridStep * 6
        val color = Ice.copy(alpha = trailStrength * 0.08f * (1f - kotlin.math.abs(index) / 7f))
        drawLine(color, center + Offset(-extent, offset), center + Offset(extent, offset), 0.5.dp.toPx())
        drawLine(color, center + Offset(offset, -extent), center + Offset(offset, extent), 0.5.dp.toPx())
    }
    SignalOrigins.forEachIndexed { index, origin ->
        val target = SignalTargets[index]
        val fraction = easedPhase(time, index * 8, ConvergedAt + index * 4)
        val start = center + origin * radius
        val end = center + target * radius
        val point = start + (end - start) * fraction
        val tail = start + (end - start) * (fraction - 0.32f).coerceAtLeast(0f)
        if (point != tail) {
            drawLine(
                brush = Brush.linearGradient(listOf(Color.Transparent, Ice.copy(alpha = trailStrength * 0.66f)), tail, point),
                start = tail,
                end = point,
                strokeWidth = 0.75.dp.toPx(),
            )
        }
        val pointAlpha = (0.24f + convergence * 0.7f) * (1f - easedPhase(time, 205, 345))
        drawCircle(Ice.copy(alpha = pointAlpha * 0.17f), 3.dp.toPx(), point)
        drawCircle(MarkWhite.copy(alpha = pointAlpha), 1.dp.toPx(), point)
    }
}

private fun DrawScope.drawLaunchMark(mark: Path, time: Float) {
    val diameter = 80.dp.toPx()
    val scale = diameter / MarkViewport
    val fill = easedPhase(time, ConvergedAt, 365)
    val outline = (0.65f + 0.25f * easedPhase(time, 0, ConvergedAt)) * (1f - fill)

    val sweep = easedPhase(time, 260, MarkRevealedAt)
    if (sweep > 0f && sweep < 1f) {
        val width = diameter * 1.8f
        val origin = center + Offset(-width / 2f, diameter * 0.19f)
        val end = center + Offset(width / 2f, -diameter * 0.19f)
        val light = origin + (end - origin) * sweep
        val glowRadius = diameter * 0.55f
        val strength = easedPhase(time, 260, 320) * (1f - easedPhase(time, 365, MarkRevealedAt))
        drawCircle(
            Brush.radialGradient(listOf(Ice.copy(alpha = 0.23f * strength), Color.Transparent), light, glowRadius),
            radius = glowRadius,
            center = light,
        )
        drawLine(
            Brush.radialGradient(listOf(Ice.copy(alpha = strength * 0.89f), Color.Transparent), light, glowRadius),
            origin, end, 1.dp.toPx(),
        )
    }

    withTransform({
        translate(center.x - diameter / 2f, center.y - diameter / 2f)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(mark, color = MarkWhite, alpha = 0.24f + fill * 0.76f)
        if (outline > 0f) drawPath(mark, color = Ice, alpha = outline, style = Stroke(0.95.dp.toPx() / scale))
    }
}

private fun easedPhase(time: Float, start: Int, end: Int): Float =
    FastOutSlowInEasing.transform(((time - start) / (end - start)).coerceIn(0f, 1f))
