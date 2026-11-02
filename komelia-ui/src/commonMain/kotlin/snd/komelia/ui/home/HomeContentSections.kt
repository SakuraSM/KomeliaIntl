package snd.komelia.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import snd.komelia.ui.common.cards.BookDetailedListCard
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalKomeliaLayout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.TextButton
import snd.komelia.ui.common.cards.SeriesDetailedListCard
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_view_all
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_continue_reading
import snd.komelia.ui.common.cards.BookImageCard
import snd.komelia.ui.common.cards.SeriesImageCard
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.SeriesMenuActions

internal fun LazyGridScope.BookFilterEntry(
    label: String,
    books: List<KomeliaBook>,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,
    featuredBook: KomeliaBook? = null,
    preview: Boolean = false,
    onViewAll: () -> Unit = {},
) {
    if (books.isEmpty()) return

    if (featuredBook != null) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "hero-${featuredBook.id.value}") {
            Box(Modifier.padding(end = 12.dp)) {
            ContinueReadingHero(
                ContinueReadingHeroContent(stringResource(Res.string.home_continue_reading), featuredBook),
                ContinueReadingHeroActions(
                    onDetails = { onBookClick(featuredBook) },
                    onRead = { onBookReadClick(featuredBook, true) },
                    menu = bookMenuActions,
                ),
            )
            }
        }
    }
    if (books.any { it.id != featuredBook?.id }) item(span = { GridItemSpan(maxLineSpan) }) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (preview && featuredBook == null) 14.dp else 0.dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.controlSpacing),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (preview) HomeViewAllButton(onViewAll)
        }
    }
    val visibleBooks = books.filterNot { it.id == featuredBook?.id }.let { if (preview) it.take(3) else it }
    if (preview && featuredBook == null) {
        items(visibleBooks, span = { GridItemSpan(maxLineSpan) }) { book ->
            BookDetailedListCard(book, onClick = { onBookClick(book) }, bookMenuActions = bookMenuActions,
                onBookReadClick = { onBookReadClick(book, it) })
        }
        return
    }
    items(visibleBooks) { book ->
        BookImageCard(
            book = book,
            onBookClick = { onBookClick(book) },
            onBookReadClick = { onBookReadClick(book, it) },
            bookMenuActions = bookMenuActions,
            showSeriesTitle = false,
            compactCaption = preview,
            modifier = Modifier.fillMaxSize()
        )
    }
}

internal fun LazyGridScope.SeriesFilterEntries(
    label: String,
    series: List<KomeliaSeries>,
    onSeriesClick: (KomeliaSeries) -> Unit,
    seriesMenuActions: SeriesMenuActions,
    preview: Boolean = false,
    onViewAll: () -> Unit = {},
) {
    if (series.isEmpty()) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = if (preview) 14.dp else 0.dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (preview) HomeViewAllButton(onViewAll)
        }
    }

    if (preview) {
        items(series.take(3), span = { GridItemSpan(maxLineSpan) }) { item ->
            SeriesDetailedListCard(item, onClick = { onSeriesClick(item) })
        }
        return
    }
    items(series) {
        SeriesImageCard(
            series = it,
            onSeriesClick = { onSeriesClick(it) },
            seriesMenuActions = seriesMenuActions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun HomeViewAllButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.requiredHeight(48.dp)) {
        Text(stringResource(Res.string.home_view_all), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
