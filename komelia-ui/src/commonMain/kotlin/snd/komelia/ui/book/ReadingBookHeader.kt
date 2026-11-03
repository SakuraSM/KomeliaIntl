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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.ui.LocalOfflineAvailable
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.ReadingDesign
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.foundation.shape.CircleShape
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
        val windowWidth = LocalWindowWidth.current
        val wide = windowWidth == WindowSizeClass.EXPANDED || windowWidth == WindowSizeClass.FULL
        val isText = book.media.mediaType == "application/epub+zip"
        val coverWidth = if (!wide && isText) detailCoverWidth(maxWidth, windowWidth).coerceAtMost(131.dp)
            else detailCoverWidth(maxWidth, windowWidth) + if (!wide) 12.dp else 0.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.then(if (!wide && isText) Modifier.padding(start = 2.dp) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(if (windowWidth == WindowSizeClass.FULL) 38.dp else if (wide) 20.dp else if (isText) 16.dp else 22.dp), verticalAlignment = Alignment.Top) {
            BookThumbnail(
                book.id,
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(coverWidth).aspectRatio(2f / 3f)
                    .clip(ReadingDesign.coverShape).background(colors.surfaceContainerLow).testTag("detail-cover"),
            )
            Column(Modifier.weight(1f).padding(top = if (!wide && !isText) 8.dp else 0.dp).testTag("detail-metadata"),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (wide && !isText) book.seriesTitle.ifBlank { book.metadata.title } else book.metadata.title,
                    style = if (wide && !isText) MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 44.sp)
                    else if (windowWidth == WindowSizeClass.FULL) MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp, lineHeight = 44.sp)
                    else if (isText) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp))
                if (wide && !isText && book.seriesTitle != book.metadata.title) Text(book.metadata.title, style = MaterialTheme.typography.headlineSmall)
                if (!wide && isText && book.metadata.number.isNotBlank()) Text(stringResource(Res.string.reading_book_number, book.metadata.number),
                    style = MaterialTheme.typography.titleSmall)
                val authors = remember(book.metadata.authors) { book.metadata.authors.map { it.name }.distinct().joinToString("、") }
                if (isText && authors.isNotBlank()) Text(authors, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (!wide && onParentSeries != null && book.seriesTitle.isNotBlank()) {
                    if (isText) Surface(onClick = onParentSeries, modifier = Modifier.height(32.dp),
                        shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHigh) {
                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.reading_parent_series, book.seriesTitle), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                        }
                    } else {
                    TextButton(onClick = onParentSeries, contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(24.dp)) {
                        Text(book.seriesTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                    }
                    }
                }
                if (!isText && authors.isNotBlank()) Text(authors, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (!wide || !isText) Text(readingMediaLabel(book), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = if (!wide && !isText) Modifier.padding(top = 5.dp) else Modifier)
                if (windowWidth == WindowSizeClass.FULL && isText) Spacer(Modifier.height(44.dp))
                if (isText) ReadingProgressSummary(book, trackHeight = if (wide) 12.dp else 6.dp)
                if (unavailable || book.remoteFileUnavailable) Text(stringResource(
                    if (unavailable) Res.string.book_unavailable else Res.string.book_remote_unavailable),
                    style = MaterialTheme.typography.bodySmall, color = if (unavailable) colors.error else colors.onSurfaceVariant)
                if ((!wide && !isText) || (wide && isText)) ReadingSecondaryActions(book, onDownload, if (wide) null else onContents, compact = !wide,
                    modifier = if (!wide) Modifier.padding(start = 5.dp, top = 16.dp) else Modifier)
            }
        }
        if (!wide && isText) ReadingSecondaryActions(book, onDownload, onContents, compact = true,
            modifier = Modifier.fillMaxWidth(), alignEnd = true)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingSecondaryActions(book: KomeliaBook, onDownload: () -> Unit, onContents: (() -> Unit)?, compact: Boolean,
    modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(if (alignEnd) 16.dp else 4.dp,
        alignment = if (alignEnd) Alignment.End else Alignment.Start)) {
        if (onContents != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedIconButton(onClick = onContents, shape = CircleShape, modifier = Modifier.size(48.dp).padding(2.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.List, stringResource(Res.string.reading_contents), Modifier.size(22.dp))
                }
                Text(stringResource(Res.string.reading_contents), style = MaterialTheme.typography.labelSmall)
            }
        }
        if (LocalOfflineAvailable.current && !book.libraryId.isLocalLibrary() &&
            (!book.downloaded || book.isLocalFileOutdated) && !book.deleted) DownloadButton(book, onDownload, compact = compact)
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
