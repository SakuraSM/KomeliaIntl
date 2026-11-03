package snd.komelia.ui.common.cards

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.book.readingMediaLabel
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactBookRow(
    book: KomeliaBook,
    onClick: (() -> Unit)?,
    menu: BookMenuActions?,
    onRead: ((Boolean) -> Unit)?,
    isSelected: Boolean,
    onSelect: (() -> Unit)?,
    compact: Boolean,
    modifier: Modifier,
    isActive: Boolean = false,
    compactCoverSlotWidth: androidx.compose.ui.unit.Dp = 80.dp,
    searchResult: Boolean = false,
    showTrailingAction: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val progress = bookReadProgressFraction(book)
    val isReading = book.readProgress != null && book.readProgress?.completed != true
    val isUnavailable = !snd.komelia.ui.common.bookContentIsAvailable(book, LocalLibraries.current.value.firstOrNull { it.id == book.libraryId }?.unavailable == true)
    val canRead = !isUnavailable && readIsSupported(book)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 96.dp else if (searchResult) 132.dp else 112.dp)
            .background(if (isSelected || isActive) colors.primary.copy(alpha = .12f) else Color.Transparent, MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick ?: {}, onLongClick = onSelect)
            .padding(horizontal = if (isSelected) 8.dp else 0.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
            verticalAlignment = if (searchResult) Alignment.Top else Alignment.CenterVertically) {
            Box(Modifier.width(if (compact) compactCoverSlotWidth else if (searchResult) 76.dp else 64.dp)
                .height(if (compact) 81.dp else if (searchResult) 114.dp else 96.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(if (compact) 64.dp else if (searchResult) 76.dp else 64.dp).fillMaxHeight()
                .clip(MaterialTheme.shapes.extraSmall).background(colors.surfaceContainerLow).testTag("catalog-row-cover")) {
                BookThumbnail(book.id, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (isSelected && onSelect != null) SelectionRadioButton(true, onSelect)
            }
            }
            Column(Modifier.weight(1f).then(if (searchResult) Modifier.padding(top = 7.dp) else Modifier).testTag("catalog-row-metadata"),
                verticalArrangement = Arrangement.spacedBy(if (searchResult) 4.dp else 3.dp)) {
                if (compact && !showTrailingAction && book.metadata.number.isNotBlank()) Text(book.metadata.number,
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                if (searchResult) Text(stringResource(Res.string.enum_book), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(book.metadata.title, style = if (searchResult) MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp) else MaterialTheme.typography.bodyMedium,
                    color = if (isReading || isSelected || isActive) colors.primary else colors.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!compact) {
                    val context = book.seriesTitle.takeIf { !searchResult && it.isNotBlank() && it != book.metadata.title && !book.oneshot }
                        ?: book.metadata.authors.map { it.name }.distinct().joinToString("、").ifBlank { null }
                    Text(if (searchResult) listOfNotNull(context, readingMediaLabel(book)).joinToString(" · ") else context ?: readingMediaLabel(book), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val status = when {
                    isUnavailable -> stringResource(Res.string.book_unavailable)
                    book.readProgress?.completed == true -> stringResource(Res.string.enum_read)
                    book.readProgress == null -> stringResource(Res.string.enum_unread)
                    progress == null -> stringResource(Res.string.enum_in_progress)
                    else -> stringResource(Res.string.home_progress_percent, (progress * 100).roundToInt())
                }
                val downloaded = if (!book.libraryId.isLocalLibrary() && book.downloaded) stringResource(Res.string.book_downloaded_status) else null
                val statusLabel = if (compact && !showTrailingAction && !isUnavailable && book.media.pagesCount > 0)
                    stringResource(Res.string.book_pages, book.media.pagesCount.toString()) else listOfNotNull(status, downloaded).joinToString(" · ")
                Text(statusLabel, style = MaterialTheme.typography.bodySmall,
                    color = if (isUnavailable) colors.error else colors.onSurfaceVariant)
                if (compact && showTrailingAction && isReading && progress != null) LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {})
            }
            if (showTrailingAction && menu == null) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            else if (showTrailingAction && menu != null) {
                var expanded by remember(book.id) { mutableStateOf(false) }
                Box(Modifier.align(Alignment.CenterVertically)) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.MoreVert, stringResource(Res.string.actions_show_more), Modifier.size(20.dp))
                    }
                    BookActionsMenu(book, menu, expanded, showEditOption = true, showDownloadOption = true,
                        onDismissRequest = { expanded = false }, onReadClick = if (canRead) onRead else null)
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .6f))
    }
}
