package snd.komelia.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.ReadingDesign
import snd.komelia.ui.book.ReadingProgressSummary
import snd.komelia.ui.book.readingActionLabel
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported

internal data class ContinueReadingHeroContent(val title: String, val book: KomeliaBook)
internal data class ContinueReadingHeroActions(val onDetails: () -> Unit, val onRead: () -> Unit, val menu: BookMenuActions)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContinueReadingHero(content: ContinueReadingHeroContent, actions: ContinueReadingHeroActions) {
    val book = content.book
    val colors = MaterialTheme.colorScheme
    var showMenu by remember(book.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(content.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
            modifier = Modifier.offset(y = (-7).dp).testTag("home-reading-heading"))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val coverWidth = (maxWidth * .3815f).coerceIn(112.dp, 176.dp)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Box {
                    BookThumbnail(book.id, contentScale = ContentScale.Fit,
                        modifier = Modifier.width(coverWidth).aspectRatio(2f / 3f)
                            .clip(ReadingDesign.coverShape).background(colors.surfaceContainerLow).testTag("home-reading-cover")
                            .semantics { contentDescription = book.metadata.title }
                            .combinedClickable(role = Role.Button, onClick = actions.onDetails,
                                onLongClickLabel = stringResource(Res.string.actions_show_more), onLongClick = { showMenu = true }))
                    BookActionsMenu(book, actions.menu, showMenu, showEditOption = true, showDownloadOption = true,
                        onDismissRequest = { showMenu = false })
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(book.seriesTitle.ifBlank { book.metadata.title }, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(role = Role.Button, onClick = actions.onDetails))
                    if (book.seriesTitle.isNotBlank() && book.seriesTitle != book.metadata.title) Text(book.metadata.title,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val authors = remember(book.metadata.authors) { book.metadata.authors.map { it.name }.distinct().joinToString("、") }
                    if (authors.isNotBlank()) Text(authors, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    HeroReadingControls(book, onRead = actions.onRead)
                }
            }
        }
    }
}

@Composable
internal fun HeroReadingControls(book: KomeliaBook,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onAccent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimary,
    onRead: () -> Unit) {
    val libraryUnavailable = LocalLibraries.current.value.firstOrNull { it.id == book.libraryId }?.unavailable == true
    val enabled = snd.komelia.ui.common.bookContentIsAvailable(book, libraryUnavailable) && readIsSupported(book)
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ReadingProgressSummary(book, accent, labelRowHeight = 25.dp)
        Surface(onClick = onRead, enabled = enabled, color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = shape) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Surface(color = if (enabled) accent else accent.copy(alpha = .12f), contentColor = if (enabled) onAccent else onAccent.copy(alpha = .38f),
                    shape = shape, modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).testTag("home-reading-action-paint")) {
                    Box(Modifier.padding(horizontal = 8.dp).heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                        Text(readingActionLabel(book), style = MaterialTheme.typography.labelLarge, maxLines = 2)
                    }
                }
            }
        }
    }
}
