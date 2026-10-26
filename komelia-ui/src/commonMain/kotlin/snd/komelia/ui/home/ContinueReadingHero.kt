package snd.komelia.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_continue_reading
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_hero_details
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_progress_percent
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.common.cards.bookReadProgressFraction
import snd.komelia.ui.common.components.GlassBackdropScope
import snd.komelia.ui.common.components.KomeliaChromeSurface
import snd.komelia.ui.common.components.glassBackdrop
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported

internal data class ContinueReadingHeroContent(val title: String, val book: KomeliaBook)
internal data class ContinueReadingHeroActions(
    val onDetails: () -> Unit,
    val onRead: () -> Unit,
    val menu: BookMenuActions,
)

@Composable
internal fun ContinueReadingHero(content: ContinueReadingHeroContent, actions: ContinueReadingHeroActions) {
    val book = content.book
    val shape = RoundedCornerShape(24.dp)
    val detailsLabel = stringResource(Res.string.home_hero_details, book.metadata.title)
    var artworkUnavailable by remember(book.id) { mutableStateOf(false) }
    GlassBackdropScope {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val height = (maxWidth * 1.15f).coerceIn(320.dp, 480.dp)
            Box(Modifier.fillMaxWidth().then(if (artworkUnavailable) Modifier else Modifier.height(height))
                .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .semantics { contentDescription = detailsLabel }
                .clickable(role = Role.Button, onClick = actions.onDetails)) {
                BookThumbnail(
                    bookId = book.id,
                    contentScale = ContentScale.Crop,
                    onLoadResult = { artworkUnavailable = !it },
                    modifier = Modifier.matchParentSize().alpha(if (artworkUnavailable) 0f else 1f).glassBackdrop(),
                )
                Column(
                    modifier = if (artworkUnavailable) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    val headerColor = if (artworkUnavailable) MaterialTheme.colorScheme.onSurface else Color.White
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(
                                if (artworkUnavailable) Color.Transparent else Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(content.title, color = headerColor, style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        HeroBookMenu(book, actions.menu, headerColor)
                    }
                    KomeliaChromeSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 4.dp,
                    ) {
                        HeroReadingControls(book, actions.onRead)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBookMenu(book: KomeliaBook, menu: BookMenuActions, color: Color) {
    var expanded by remember(book.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreHoriz, stringResource(Res.string.actions_show_more), tint = color)
        }
        BookActionsMenu(
            book = book,
            actions = menu,
            expanded = expanded,
            showEditOption = true,
            showDownloadOption = true,
            onDismissRequest = { expanded = false },
        )
    }
}

@Composable
internal fun HeroReadingControls(book: KomeliaBook, onRead: () -> Unit) {
    val progress = bookReadProgressFraction(book)
    val libraryUnavailable = LocalLibraries.current.value.firstOrNull { it.id == book.libraryId }?.unavailable == true
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.2f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HeroBookTitle(book)
                    HeroReadButton(book, onRead, libraryUnavailable)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroBookTitle(book, Modifier.weight(1f))
                    HeroReadButton(book, onRead, libraryUnavailable)
                }
            }
        }
        if (progress != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(4.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
                drawStopIndicator = {})
            Text(stringResource(Res.string.home_progress_percent, (progress * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeroBookTitle(book: KomeliaBook, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(book.seriesTitle.ifBlank { book.metadata.title }, style = MaterialTheme.typography.titleLarge,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (book.seriesTitle.isNotBlank() && book.metadata.title != book.seriesTitle) {
            Text(book.metadata.title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroReadButton(book: KomeliaBook, onRead: () -> Unit, libraryUnavailable: Boolean) {
    TextButton(onClick = onRead, enabled = !book.deleted && !libraryUnavailable && readIsSupported(book),
        modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(stringResource(Res.string.home_continue_reading))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.padding(start = 4.dp))
    }
}
