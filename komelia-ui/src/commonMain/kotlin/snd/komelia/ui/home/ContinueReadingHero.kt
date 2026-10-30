package snd.komelia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.book.ReadingProgressSummary
import snd.komelia.ui.book.readingActionLabel
import snd.komelia.ui.book.readingMediaLabel
import snd.komelia.ui.common.components.GlassBackdropScope
import snd.komelia.ui.common.components.glassBackdrop
import snd.komelia.ui.common.components.KomeliaChromeSurface
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported

internal data class ContinueReadingHeroContent(val title: String, val book: KomeliaBook)
internal data class ContinueReadingHeroActions(val onDetails: () -> Unit, val onRead: () -> Unit, val menu: BookMenuActions)

@Composable
internal fun ContinueReadingHero(content: ContinueReadingHeroContent, actions: ContinueReadingHeroActions) {
    val book = content.book
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(content.title, style = MaterialTheme.typography.titleLarge)
        HeroReadingSurface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    BookThumbnail(book.id, contentScale = ContentScale.Fit,
                        modifier = Modifier.width(80.dp).aspectRatio(2f / 3f)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .semantics { contentDescription = book.metadata.title }
                            .clickable(role = Role.Button, onClick = actions.onDetails))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(book.metadata.title, style = MaterialTheme.typography.titleLarge,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = actions.onDetails))
                        if (book.seriesTitle.isNotBlank() && book.seriesTitle != book.metadata.title) Text(book.seriesTitle,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val authors = remember(book.metadata.authors) { book.metadata.authors.map { it.name }.distinct().joinToString("、") }
                        if (authors.isNotBlank()) Text(authors, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(readingMediaLabel(book), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HeroBookMenu(book, actions.menu)
                }
                HeroReadingControls(book, colors.primary, colors.onPrimary, actions.onRead)
            }
        }
    }
}

@Composable
private fun HeroBookMenu(book: KomeliaBook, menu: BookMenuActions) {
    var expanded by remember(book.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.actions_show_more))
        }
        BookActionsMenu(book, menu, expanded, showEditOption = true, showDownloadOption = true,
            onDismissRequest = { expanded = false })
    }
}

@Composable
internal fun HeroReadingControls(book: KomeliaBook,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onAccent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimary,
    onRead: () -> Unit) {
    val libraryUnavailable = LocalLibraries.current.value.firstOrNull { it.id == book.libraryId }?.unavailable == true
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ReadingProgressSummary(book, accent)
        Button(onClick = onRead, enabled = snd.komelia.ui.common.bookContentIsAvailable(book, libraryUnavailable) && readIsSupported(book),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent)) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(readingActionLabel(book))
        }
    }
}

@Composable
private fun HeroReadingSurface(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    GlassBackdropScope {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.matchParentSize().clip(shape).glassBackdrop().background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(colors.surfaceContainerHighest, colors.surfaceContainerLow))))
            KomeliaChromeSurface(Modifier.fillMaxWidth(), shape = shape, content = content)
        }
    }
}
