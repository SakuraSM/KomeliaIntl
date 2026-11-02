package snd.komelia.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import snd.komelia.ui.common.images.BookThumbnail
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.cards.BookDetailedListCard
import snd.komelia.ui.common.components.KomeliaModalBottomSheet
import snd.komelia.ui.reader.EpubScreen
import snd.komelia.ui.readerHostNavigator
import snd.komga.client.book.WPLink

class BookReadingNavigationState(private val api: KomgaBookApi, private val book: KomeliaBook, private val scope: CoroutineScope) {
    val state = MutableStateFlow<LoadState<Unit>>(LoadState.Uninitialized)
    val chapters = MutableStateFlow<List<WPLink>>(emptyList())
    val adjacentBooks = MutableStateFlow(BookAdjacentBooks())
    fun load() {
        scope.launch {
            state.value = LoadState.Loading
            try {
                if (book.media.mediaType == "application/epub+zip") chapters.value = api.getWebPubManifest(book.id).toc
                else if (!book.oneshot) adjacentBooks.value = coroutineScope {
                    val previous = async { api.getBookSiblingPrevious(book.id) }
                    val next = async { api.getBookSiblingNext(book.id) }
                    BookAdjacentBooks(previous.await(), next.await())
                }
                state.value = LoadState.Success(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { state.value = LoadState.Error(error) }
        }
    }
}

data class BookAdjacentBooks(val previous: KomeliaBook? = null, val next: KomeliaBook? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookReadingNavigation(
    book: KomeliaBook,
    context: BookSiblingsContext,
    navigation: BookReadingNavigationState,
    showContents: Boolean,
    onShowContents: (Boolean) -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val chapters = navigation.chapters.collectAsState().value
    val adjacent = navigation.adjacentBooks.collectAsState().value
    val loadState = navigation.state.collectAsState().value
    val openChapter: (WPLink) -> Unit = { chapter ->
        chapter.href?.let { href ->
            onShowContents(false)
            navigator.readerHostNavigator().push(EpubScreen(book.id, context, book = book, initialChapterHref = href))
        }
    }
    if (loadState is LoadState.Error && !showContents) TextButton(onClick = navigation::load) {
        Text(stringResource(Res.string.reader_sibling_retry))
    }
    if (chapters.isNotEmpty()) Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.reading_contents), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { onShowContents(true) }) { Text(stringResource(Res.string.reading_all_contents)) }
        }
        chapters.take(3).forEach { ChapterRow(it, openChapter) }
    }
    if (book.media.mediaType != "application/epub+zip" && !book.oneshot && loadState is LoadState.Success) Column {
        Text(stringResource(Res.string.reading_adjacent), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdjacentBookCard(adjacent.previous, stringResource(Res.string.reader_previous_book),
                stringResource(Res.string.reader_no_previous_book), Modifier.weight(1f)) { navigator.push(bookScreen(it)) }
            VerticalDivider(Modifier.height(88.dp), color = MaterialTheme.colorScheme.outlineVariant)
            AdjacentBookCard(adjacent.next, stringResource(Res.string.reader_next_book),
                stringResource(Res.string.reader_no_next_book), Modifier.weight(1f)) { navigator.push(bookScreen(it)) }
        }
    }
    if (showContents) KomeliaModalBottomSheet(onDismissRequest = { onShowContents(false) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.reading_contents), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { onShowContents(false) }) {
                    Icon(Icons.Rounded.Close, stringResource(Res.string.navigation_close_settings))
                }
            }
            Text(book.metadata.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loadState is LoadState.Loading || loadState is LoadState.Uninitialized) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (loadState is LoadState.Error) TextButton(onClick = navigation::load) { Text(stringResource(Res.string.reader_sibling_retry)) }
            else if (chapters.isEmpty()) Text(stringResource(Res.string.reading_no_contents))
            chapters.forEach { ChapterBranch(it, openChapter) }
        }
    }
}

@Composable
private fun AdjacentBookCard(book: KomeliaBook?, label: String, boundary: String, modifier: Modifier, onOpen: (KomeliaBook) -> Unit) {
    Surface(onClick = { book?.let(onOpen) }, enabled = book != null,
        color = androidx.compose.ui.graphics.Color.Transparent, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(Modifier.heightIn(min = 88.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (book != null) BookThumbnail(book.id, contentScale = ContentScale.Fit,
                modifier = Modifier.width(44.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.extraSmall))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(book?.metadata?.title ?: boundary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
            }
            if (book != null) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ChapterBranch(chapter: WPLink, onOpen: (WPLink) -> Unit, depth: Int = 0) {
    Column(Modifier.padding(start = if (depth == 0) 0.dp else 16.dp)) {
        var expanded by remember(chapter.href, chapter.title) { mutableStateOf(depth == 0) }
        ChapterRow(chapter, onOpen, expanded = expanded,
            onExpand = if (chapter.children.isNotEmpty()) { { expanded = !expanded } } else null)
        if (expanded) chapter.children.forEach { ChapterBranch(it, onOpen, depth + 1) }
    }
}

@Composable
private fun ChapterRow(chapter: WPLink, onOpen: (WPLink) -> Unit, expanded: Boolean = false, onExpand: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .clickable(enabled = chapter.href != null) { onOpen(chapter) }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(chapter.title ?: chapter.href.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (onExpand != null) IconButton(onClick = onExpand) {
            Icon(if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                stringResource(Res.string.reading_expand_chapter))
        } else if (chapter.href != null) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
