package snd.komelia.image

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Separable, premultiplied-alpha upsampling. Input bytes are gray/gray-alpha/RGB/RGBA; output is ARGB. */
suspend fun upscalePixels(
    pixels: ByteArray,
    width: Int,
    height: Int,
    bands: Int,
    targetWidth: Int,
    targetHeight: Int,
    kernel: ReduceKernel,
    sourceRegion: ImageRect = ImageRect(0, 0, width, height),
): IntArray {
    require(width > 0 && height > 0 && bands in 1..4)
    require(width.toLong() * height * bands == pixels.size.toLong())
    require(targetWidth > 0 && targetHeight > 0 && targetWidth.toLong() * targetHeight <= Int.MAX_VALUE)
    require(sourceRegion.left >= 0 && sourceRegion.top >= 0 && sourceRegion.right <= width && sourceRegion.bottom <= height)
    require(sourceRegion.width > 0 && sourceRegion.height > 0)
    require(targetWidth >= sourceRegion.width && targetHeight >= sourceRegion.height)
    val radius = when (kernel) {
        ReduceKernel.MITCHELL -> 2
        ReduceKernel.LANCZOS3 -> 3
        else -> error("Unsupported upsampling kernel: $kernel")
    }
    val context = currentCoroutineContext()
    context.ensureActive()
    val horizontal = samplingAxis(width, sourceRegion.left, sourceRegion.width, targetWidth, radius, kernel)
    val vertical = samplingAxis(height, sourceRegion.top, sourceRegion.height, targetHeight, radius, kernel)
    val taps = radius * 2
    // Cache only the few source rows needed by the vertical kernel, rather than a full intermediate image.
    val rowIds = IntArray(taps) { -1 }
    val rows = Array(taps) { FloatArray(targetWidth * 4) }
    val outputRow = FloatArray(targetWidth * 4)
    val output = IntArray(targetWidth * targetHeight)
    val gray = bands <= 2
    val hasAlpha = bands == 2 || bands == 4
    for (y in 0 until targetHeight) {
        context.ensureActive()
        outputRow.fill(0f)
        for (tapY in 0 until taps) {
            val sourceY = vertical.indices[y * taps + tapY]
            val weightY = vertical.weights[y * taps + tapY]
            if (weightY == 0f) continue
            val slot = sourceY % taps
            val row = rows[slot]
            if (rowIds[slot] != sourceY) {
                row.fill(0f)
                for (x in 0 until targetWidth) {
                    val destination = x * 4
                    for (tapX in 0 until taps) {
                        val sourceX = horizontal.indices[x * taps + tapX]
                        val weightX = horizontal.weights[x * taps + tapX]
                        val source = (sourceY * width + sourceX) * bands
                        val alpha = if (hasAlpha) pixels[source + bands - 1].toInt() and 255 else 255
                        val premultipliedWeight = weightX * (alpha / 255f)
                        row[destination] += (pixels[source].toInt() and 255) * premultipliedWeight
                        row[destination + 1] += (pixels[source + if (gray) 0 else 1].toInt() and 255) * premultipliedWeight
                        row[destination + 2] += (pixels[source + if (gray) 0 else 2].toInt() and 255) * premultipliedWeight
                        row[destination + 3] += alpha * weightX
                    }
                }
                rowIds[slot] = sourceY
            }
            for (i in outputRow.indices) outputRow[i] += row[i] * weightY
        }
        for (x in 0 until targetWidth) {
            val offset = x * 4
            val alpha = outputRow[offset + 3]
            val a = alpha.roundToInt().coerceIn(0, 255)
            if (a == 0) continue
            val unpremultiply = 255f / alpha
            val r = (outputRow[offset] * unpremultiply).roundToInt().coerceIn(0, 255)
            val g = (outputRow[offset + 1] * unpremultiply).roundToInt().coerceIn(0, 255)
            val b = (outputRow[offset + 2] * unpremultiply).roundToInt().coerceIn(0, 255)
            output[y * targetWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return output
}

private class SamplingAxis(val indices: IntArray, val weights: FloatArray)

private fun samplingAxis(
    sourceSize: Int,
    offset: Int,
    regionSize: Int,
    targetSize: Int,
    radius: Int,
    kernel: ReduceKernel,
): SamplingAxis {
    val taps = radius * 2
    val indices = IntArray(targetSize * taps)
    val weights = FloatArray(targetSize * taps)
    for (position in 0 until targetSize) {
        val center = offset + (position + .5) * regionSize / targetSize - .5
        val first = floor(center).toInt() - radius + 1
        var sum = 0f
        for (tap in 0 until taps) {
            val index = position * taps + tap
            indices[index] = (first + tap).coerceIn(0, sourceSize - 1)
            val x = abs(center - first - tap)
            weights[index] = when (kernel) {
                ReduceKernel.MITCHELL -> mitchellWeight(x)
                else -> lanczos3Weight(x)
            }.toFloat()
            sum += weights[index]
        }
        for (tap in 0 until taps) weights[position * taps + tap] /= sum
    }
    return SamplingAxis(indices, weights)
}

// Mitchell-Netravali with B = C = 1/3.
private fun mitchellWeight(x: Double): Double = when {
    x < 1.0 -> ((7.0 * x - 12.0) * x * x + 16.0 / 3.0) / 6.0
    x < 2.0 -> (((-7.0 / 3.0 * x + 12.0) * x - 20.0) * x + 32.0 / 3.0) / 6.0
    else -> 0.0
}

private fun lanczos3Weight(x: Double): Double = when {
    x < 1e-9 -> 1.0
    x >= 3.0 -> 0.0
    else -> sin(PI * x) * sin(PI * x / 3.0) / (PI * PI * x * x / 3.0)
}
