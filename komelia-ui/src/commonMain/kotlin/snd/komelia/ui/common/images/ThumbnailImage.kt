package snd.komelia.ui.common.images

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision

@Composable
fun ThumbnailImage(
    data: Any,
    cacheKey: String,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: Painter? = null,
    modifier: Modifier = Modifier,
    onLoadResult: ((Boolean) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val request = remember(data, cacheKey) {
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .precision(Precision.EXACT)
            .crossfade(true)
            .build()
    }

    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val outline = MaterialTheme.colorScheme.outlineVariant
    val coverIcon = rememberVectorPainter(Icons.AutoMirrored.Outlined.MenuBook)
    val fallback = remember(surface, outline, coverIcon) { CoverPlaceholderPainter(surface, outline, coverIcon) }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier,
        placeholder = placeholder ?: fallback,
        error = fallback,
        fallback = fallback,
        contentScale = contentScale,
        onSuccess = { onLoadResult?.invoke(true) },
        onError = { onLoadResult?.invoke(false) },
        filterQuality = FilterQuality.Medium
    )
}

private class CoverPlaceholderPainter(
    private val surface: Color,
    private val outline: Color,
    private val icon: Painter,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified
    override fun DrawScope.onDraw() {
        drawRect(surface)
        val side = size.minDimension * .24f
        translate((size.width - side) / 2, (size.height - side) / 2) {
            with(icon) { draw(Size(side, side), colorFilter = ColorFilter.tint(outline)) }
        }
    }
}
