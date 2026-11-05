package snd.komelia.ui.book

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.common.BookReadButton
import snd.komelia.ui.common.cards.bookReadProgressFraction
import snd.komelia.ui.common.components.KomeliaChromeSurface
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.platform.WindowSizeClass

@Composable
internal fun ReadingActionFooter(book: KomeliaBook, onRead: (Boolean) -> Unit, modifier: Modifier = Modifier,
    horizontalInset: Dp = 0.dp) {
    val colors = MaterialTheme.colorScheme
    val isFloating = horizontalInset > 0.dp
    val desktop = LocalWindowWidth.current == WindowSizeClass.FULL
    val tablet = LocalWindowWidth.current == WindowSizeClass.EXPANDED
    val isText = book.media.mediaType == "application/epub+zip"
    val footerModifier = if (isFloating) modifier.navigationBarsPadding()
        .padding(start = horizontalInset, end = horizontalInset, bottom = if (desktop) 32.dp else 46.dp) else modifier
    KomeliaChromeSurface(footerModifier.fillMaxWidth().testTag("reading-action-footer"), shape = if (isFloating) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        if (desktop) DesktopReadingAction(book, onRead, colors)
        else Column(Modifier.fillMaxWidth().then(if (isFloating) Modifier else Modifier.navigationBarsPadding())
            .padding(horizontal = if (tablet) 14.dp else if (isText) 20.dp else 23.dp)
            .padding(top = if (tablet) 24.dp else if (isText) 16.dp else 21.dp,
                bottom = if (tablet) 24.dp else if (isText) 23.dp else 7.dp),
            verticalArrangement = Arrangement.spacedBy(if (tablet) 18.dp else if (isText) 11.dp else 14.dp)) {
            if (isText && !tablet) InlineReadingProgress(book, colors.primary)
            else ReadingProgressSummary(book, colors.primary, labelRowHeight = if (tablet) 24.dp else 21.dp,
                trackHeight = if (tablet) 10.dp else 7.dp, labelOffset = if (tablet) 0.dp else (-3).dp,
                trackTag = "reading-footer-progress")
            BookReadButton(
                modifier = Modifier.fillMaxWidth().height(if (tablet) 52.dp else 48.dp).testTag("reading-primary-action"),
                onRead = { onRead(true) },
                label = readingActionLabel(book),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                showIcon = false,
                paintedHeight = if (tablet) 52.dp else if (isText) 48.dp else 42.dp,
            )
        }
    }
}

@Composable
private fun DesktopReadingAction(book: KomeliaBook, onRead: (Boolean) -> Unit, colors: ColorScheme) {
    val progress = bookReadProgressFraction(book)
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DesktopReadingProgress(book, progress, colors, Modifier.fillMaxWidth())
            DesktopReadingButton(book, onRead, colors, Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DesktopReadingProgress(book, progress, colors, Modifier.weight(1f))
            DesktopReadingButton(book, onRead, colors, Modifier.width(270.dp))
        }
    }
}

@Composable
private fun DesktopReadingProgress(book: KomeliaBook, progress: Float?, colors: ColorScheme, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(readingProgressLabel(book, progress), style = MaterialTheme.typography.bodyMedium)
        if (progress != null) Box(Modifier.weight(1f).height(12.dp).testTag("reading-footer-progress")) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
                color = colors.primary, trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
private fun DesktopReadingButton(book: KomeliaBook, onRead: (Boolean) -> Unit, colors: ColorScheme, modifier: Modifier) {
    BookReadButton(modifier = modifier.height(64.dp), onRead = { onRead(true) },
        label = readingActionLabel(book), containerColor = colors.primary, contentColor = colors.onPrimary,
        paintedHeight = 62.dp, labelStyle = MaterialTheme.typography.titleLarge,
        leadingIconSize = 32.dp, iconSpacing = 12.dp,
        labelAutoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 24.sp, stepSize = 1.sp))
}

@Composable
internal fun ReadingProgressSummary(book: KomeliaBook, accent: Color = MaterialTheme.colorScheme.primary,
    labelRowHeight: Dp = 21.dp, trackHeight: Dp = 3.dp, labelOffset: Dp = 0.dp, trackTag: String? = null) {
    val progress = bookReadProgressFraction(book)
    val isReading = book.readProgress != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = labelRowHeight), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(readingProgressLabel(book, progress), style = MaterialTheme.typography.labelMedium, modifier = Modifier.offset(y = labelOffset))
            if (isReading && book.media.mediaType != "application/epub+zip" && book.media.pagesCount > 0) {
                Text("${book.readProgress?.page} / ${book.media.pagesCount}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isReading && progress != null) Box(Modifier.fillMaxWidth().height(trackHeight)
            .then(if (trackTag != null) Modifier.testTag(trackTag) else Modifier)) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = accent,
                trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
private fun InlineReadingProgress(book: KomeliaBook, accent: Color) {
    val colors = MaterialTheme.colorScheme
    val progress = bookReadProgressFraction(book)
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(readingProgressLabel(book, progress), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp))
        if (progress != null) Box(Modifier.weight(1f).height(6.dp).testTag("reading-footer-progress")) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
                color = accent, trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
private fun readingProgressLabel(book: KomeliaBook, progress: Float?): String = when {
    book.readProgress == null -> stringResource(Res.string.enum_unread)
    book.readProgress?.completed == true -> stringResource(Res.string.enum_read)
    progress == null -> stringResource(Res.string.enum_in_progress)
    else -> stringResource(Res.string.home_progress_percent, (progress * 100).roundToInt())
}

@Composable
internal fun readingActionLabel(book: KomeliaBook): String = stringResource(when {
    book.readProgress?.completed == true -> Res.string.reading_again
    book.readProgress == null -> Res.string.reading_start
    else -> Res.string.home_continue_reading
})
