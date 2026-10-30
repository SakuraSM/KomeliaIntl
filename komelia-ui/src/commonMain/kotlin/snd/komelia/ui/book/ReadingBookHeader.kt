package snd.komelia.ui.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.ui.LocalOfflineAvailable
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.detailCoverWidth
import snd.komelia.ui.platform.WindowSizeClass

@Composable
internal fun ReadingBookHeader(
    book: KomeliaBook,
    onParentSeries: (() -> Unit)?,
    onDownload: () -> Unit,
    onContents: (() -> Unit)? = null,
    unavailable: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val coverWidth = detailCoverWidth(maxWidth, LocalWindowWidth.current)
        val windowWidth = LocalWindowWidth.current
        val wide = windowWidth == WindowSizeClass.EXPANDED || windowWidth == WindowSizeClass.FULL
        Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 16.dp), verticalAlignment = Alignment.Top) {
            BookThumbnail(
                book.id,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(coverWidth).aspectRatio(2f / 3f)
                    .clip(MaterialTheme.shapes.small).background(colors.surfaceContainerLow),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(book.metadata.title, style = if (windowWidth == WindowSizeClass.FULL) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall)
                if (onParentSeries != null && book.seriesTitle.isNotBlank() && book.seriesTitle != book.metadata.title) {
                    TextButton(onClick = onParentSeries, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(book.seriesTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
                val authors = remember(book.metadata.authors) { book.metadata.authors.map { it.name }.distinct().joinToString("、") }
                if (authors.isNotBlank()) Text(authors, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Text(readingMediaLabel(book), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (unavailable || book.remoteFileUnavailable) Text(stringResource(
                    if (unavailable) Res.string.book_unavailable else Res.string.book_remote_unavailable),
                    style = MaterialTheme.typography.bodySmall, color = if (unavailable) colors.error else colors.onSurfaceVariant)
                ReadingSecondaryActions(book, onDownload, onContents)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingSecondaryActions(book: KomeliaBook, onDownload: () -> Unit, onContents: (() -> Unit)?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onContents != null) TextButton(onClick = onContents, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(Res.string.reading_contents))
        }
        if (LocalOfflineAvailable.current && !book.libraryId.isLocalLibrary() &&
            (!book.downloaded || book.isLocalFileOutdated) && !book.deleted) DownloadButton(book, onDownload)
    }
}

@Composable
internal fun readingMediaLabel(book: KomeliaBook): String {
    val format = when (book.media.mediaType) {
        "application/epub+zip" -> "EPUB"
        "application/pdf" -> "PDF"
        else -> stringResource(Res.string.reading_comic)
    }
    val pageCount = if (book.media.mediaType != "application/epub+zip" && book.media.pagesCount > 0) {
        stringResource(Res.string.book_pages, book.media.pagesCount.toString())
    } else null
    val downloaded = if (book.downloaded || book.libraryId.isLocalLibrary()) stringResource(Res.string.book_downloaded_status) else null
    return listOfNotNull(format, pageCount, downloaded).joinToString(" · ")
}
