package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.LayoutDirection.Ltr
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Size
import coil3.size.SizeResolver
import org.jetbrains.compose.resources.stringResource
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_next_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_previous_page
import snd.komelia.image.ReaderImage
import snd.komelia.image.coil.BookPageThumbnailRequest
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.reader.image.PageMetadata
import kotlin.math.roundToInt

@Composable
fun ProgressSlider(
    pages: List<PageMetadata>,
    currentPageIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    show: Boolean,
    layoutDirection: LayoutDirection,
    modifier: Modifier = Modifier,
) {
    PageSpreadProgressSlider(
        pageSpreads = pages.map { listOf(it) },
        currentSpreadIndex = currentPageIndex,
        onPageNumberChange = onPageNumberChange,
        show = show,
        layoutDirection = layoutDirection,
        modifier = modifier
    )
}

@Composable
fun PageSpreadProgressSlider(
    pageSpreads: List<List<PageMetadata>>,
    currentSpreadIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    show: Boolean,
    layoutDirection: LayoutDirection,
    modifier: Modifier = Modifier,
) {
    if (pageSpreads.isEmpty()) return

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier.then(
            Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
        )
    ) {
        if (show || isHovered.value) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Slider(
                        pageSpreads = pageSpreads,
                        currentSpreadIndex = currentSpreadIndex,
                        onPageNumberChange = onPageNumberChange,
                        layoutDirection = layoutDirection
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Slider(
    pageSpreads: List<List<PageMetadata>>,
    currentSpreadIndex: Int,
    onPageNumberChange: (Int) -> Unit,
    layoutDirection: LayoutDirection,
) {
    var currentPos by remember(currentSpreadIndex) { mutableStateOf(currentSpreadIndex) }
    val currentSpread = remember(pageSpreads, currentPos) { pageSpreads.getOrElse(currentPos) { pageSpreads.last() } }
    val label = remember(currentSpread, pageSpreads, currentPos) {
        val spread = when (layoutDirection) {
            Ltr -> currentSpread
            Rtl -> currentSpread.reversed()
        }
        spread.map { it.pageNumber }.joinToString("-")

    }

    var showPreview by remember { mutableStateOf(false) }
    val sliderValue by derivedStateOf { currentPos.toFloat() }

    val sliderState = rememberSliderState(
        value = sliderValue,
        onValueChange = {
            showPreview = true
            currentPos = it.roundToInt()
        },
        onValueChangeFinished = {
            onPageNumberChange(currentPos)
            showPreview = false
        },
        steps = remember(pageSpreads.size) { (pageSpreads.size - 2).coerceAtLeast(0) },
        valueRange = remember(pageSpreads.size) { 0f..(pageSpreads.size - 1).toFloat() },
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showPreview) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(16.dp))
                    .padding(6.dp)
            ) {
                for (pageMetadata in currentSpread) {
                    BookPageThumbnail(
                        page = pageMetadata,
                        modifier = Modifier.height(300.dp).widthIn(min = 210.dp)
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    enabled = currentPos > 0,
                    onClick = {
                        currentPos = (currentPos - 1).coerceAtLeast(0)
                        onPageNumberChange(currentPos)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(Res.string.reader_previous_page),
                    )
                }

                Text(
                    text = label,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.defaultMinSize(minWidth = 28.dp),
                )

                Slider(
                    state = sliderState,
                    modifier = Modifier.weight(1f),
                    colors = AppSliderDefaults.colors(),
                    track = { state ->
                        SliderDefaults.Track(
                            sliderState = state,
                            colors = AppSliderDefaults.colors(),
                        )
                    }
                )

                Text(
                    text = pageSpreads.size.toString(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.defaultMinSize(minWidth = 28.dp),
                )

                IconButton(
                    enabled = currentPos < pageSpreads.lastIndex,
                    onClick = {
                        currentPos = (currentPos + 1).coerceAtMost(pageSpreads.lastIndex)
                        onPageNumberChange(currentPos)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(Res.string.reader_next_page),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookPageThumbnail(
    page: PageMetadata,
//    image: ImageResult?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .border(BorderStroke(2.dp, MaterialTheme.colorScheme.surface), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val context = LocalPlatformContext.current
        val request = remember(page) {
            val pageId = page.toPageId()
            ImageRequest.Builder(context)
                .data(BookPageThumbnailRequest(page.bookId, page.pageNumber))
                .memoryCacheKey(pageId.toString())
                .diskCacheKey(pageId.toString())
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }

//        val painter = remember(image) { image?.image?.asPainter(context) }
//        if (painter != null) {
//            Image(
//                painter = painter,
//                contentDescription = null,
//                contentScale = ContentScale.Fit,
//                modifier = modifier
//            )
//        }
        AsyncImage(request, null)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun rememberSliderState(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
): SliderState {

    val state = remember(steps, valueRange) {
        SliderState(value, steps, onValueChangeFinished, valueRange)
    }

    state.onValueChangeFinished = onValueChangeFinished
    state.onValueChange = onValueChange
    state.value = value
    return state
}
