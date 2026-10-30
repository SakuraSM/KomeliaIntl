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

@Composable
internal fun ReadingActionFooter(book: KomeliaBook, onRead: (Boolean) -> Unit, modifier: Modifier = Modifier,
    horizontalInset: Dp = 0.dp) {
    val colors = MaterialTheme.colorScheme
    val isFloating = horizontalInset > 0.dp
    val footerModifier = if (isFloating) modifier.navigationBarsPadding()
        .padding(start = horizontalInset, end = horizontalInset, bottom = 24.dp) else modifier
    KomeliaChromeSurface(footerModifier.fillMaxWidth(), shape = if (isFloating) RoundedCornerShape(20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.fillMaxWidth().then(if (isFloating) Modifier else Modifier.navigationBarsPadding())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReadingProgressSummary(book, colors.primary)
            BookReadButton(
                modifier = Modifier.fillMaxWidth(),
                onRead = { onRead(true) },
                label = readingActionLabel(book),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            )
        }
    }
}

@Composable
internal fun ReadingProgressSummary(book: KomeliaBook, accent: Color = MaterialTheme.colorScheme.primary) {
    val progress = bookReadProgressFraction(book)
    val isReading = book.readProgress != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when {
                !isReading -> stringResource(Res.string.enum_unread)
                book.readProgress?.completed == true -> stringResource(Res.string.enum_read)
                progress == null -> stringResource(Res.string.enum_in_progress)
                else -> stringResource(Res.string.home_progress_percent, (progress * 100).roundToInt())
            }, style = MaterialTheme.typography.labelMedium)
            if (isReading && book.media.mediaType != "application/epub+zip" && book.media.pagesCount > 0) {
                Text("${book.readProgress?.page} / ${book.media.pagesCount}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isReading && progress != null) LinearProgressIndicator(progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp), color = accent, drawStopIndicator = {})
    }
}

@Composable
internal fun readingActionLabel(book: KomeliaBook): String = stringResource(when {
    book.readProgress?.completed == true -> Res.string.reading_again
    book.readProgress == null -> Res.string.reading_start
    else -> Res.string.home_continue_reading
})
