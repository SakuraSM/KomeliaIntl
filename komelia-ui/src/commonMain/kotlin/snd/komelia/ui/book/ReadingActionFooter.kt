package snd.komelia.ui.book

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
    val footerModifier = if (isFloating) modifier.navigationBarsPadding()
        .padding(start = horizontalInset, end = horizontalInset, bottom = if (desktop) 30.dp else 24.dp) else modifier
    KomeliaChromeSurface(footerModifier.fillMaxWidth(), shape = if (isFloating) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        if (desktop) DesktopReadingAction(book, onRead, colors)
        else Column(Modifier.fillMaxWidth().then(if (isFloating) Modifier else Modifier.navigationBarsPadding())
            .padding(start = 24.dp, end = 24.dp, top = 21.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ReadingProgressSummary(book, colors.primary)
            BookReadButton(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 3.dp),
                onRead = { onRead(true) },
                label = readingActionLabel(book),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                showIcon = false,
            )
        }
    }
}

@Composable
private fun DesktopReadingAction(book: KomeliaBook, onRead: (Boolean) -> Unit, colors: ColorScheme) {
    val progress = bookReadProgressFraction(book)
    Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(readingProgressLabel(book, progress), style = MaterialTheme.typography.bodyMedium)
            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(8.dp),
                color = colors.primary, trackColor = colors.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        }
        BookReadButton(modifier = Modifier.width(270.dp).height(64.dp), onRead = { onRead(true) },
            label = readingActionLabel(book), containerColor = colors.primary, contentColor = colors.onPrimary)
    }
}

@Composable
internal fun ReadingProgressSummary(book: KomeliaBook, accent: Color = MaterialTheme.colorScheme.primary, labelRowHeight: Dp = 21.dp) {
    val progress = bookReadProgressFraction(book)
    val isReading = book.readProgress != null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = labelRowHeight), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(readingProgressLabel(book, progress), style = MaterialTheme.typography.labelMedium)
            if (isReading && book.media.mediaType != "application/epub+zip" && book.media.pagesCount > 0) {
                Text("${book.readProgress?.page} / ${book.media.pagesCount}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isReading && progress != null) LinearProgressIndicator(progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp), color = accent,
            trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
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
