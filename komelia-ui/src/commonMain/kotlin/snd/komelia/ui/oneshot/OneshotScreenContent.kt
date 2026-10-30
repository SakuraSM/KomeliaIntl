package snd.komelia.ui.oneshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_detail_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.collection.SeriesCollectionsContent
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.OneshotActionsMenu
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.series.view.SeriesDescriptionRow
import snd.komga.client.collection.KomgaCollection
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.readlist.KomgaReadList

@Composable
fun OneshotScreenContent(
    series: KomeliaSeries,
    book: KomeliaBook,
    library: KomgaLibrary,
    onLibraryClick: (KomgaLibrary) -> Unit,
    onBookReadClick: (markReadProgress: Boolean) -> Unit,
    oneshotMenuActions: BookMenuActions,

    collections: Map<KomgaCollection, List<KomeliaSeries>>,
    onCollectionClick: (KomgaCollection) -> Unit,
    onSeriesClick: (KomeliaSeries) -> Unit,

    readLists: Map<KomgaReadList, List<KomeliaBook>>,
    onReadListClick: (KomgaReadList) -> Unit,
    onReadlistBookClick: (KomeliaBook, KomgaReadList) -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    onBookDownload: () -> Unit,
    onBookDownloadDelete: () -> Unit,
    cardWidth: Dp,
    readingContext: snd.komelia.ui.BookSiblingsContext = snd.komelia.ui.BookSiblingsContext.Series,
) {
    val navigator = LocalNavigator.currentOrThrow
    snd.komelia.ui.book.BookScreenContent(
        library = library, book = book, bookMenuActions = oneshotMenuActions,
        onBookReadPress = onBookReadClick, onBookDownload = onBookDownload,
        onBookDownloadDelete = onBookDownloadDelete,
        readLists = readLists, onReadListClick = onReadListClick,
        onReadListBookPress = onReadlistBookClick, onParentSeriesPress = {},
        onFilterClick = onFilterClick, onBackPress = { navigator.pop() }, cardWidth = cardWidth,
        readingContext = readingContext,
        publisher = series.metadata.publisher, genres = series.metadata.genres,
        customToolbar = { _, onRead -> OneshotToolBar(series, book, oneshotMenuActions, onReadClick = onRead) },
        extraDetails = {
            SeriesDescriptionRow(library = library, onLibraryClick = onLibraryClick,
                releaseDate = null, status = null, ageRating = series.metadata.ageRating,
                language = series.metadata.language, readingDirection = series.metadata.readingDirection,
                deleted = series.deleted || library.unavailable, alternateTitles = series.metadata.alternateTitles,
                onFilterClick = onFilterClick, modifier = Modifier)
            if (collections.isNotEmpty()) SeriesCollectionsContent(collections, onCollectionClick, onSeriesClick, cardWidth)
        },
    )
}

@Composable
fun OneshotToolBar(
    series: KomeliaSeries,
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    floating: Boolean = false,
    onReadClick: ((Boolean) -> Unit)? = null,
) {
    val navigator = LocalNavigator.currentOrThrow
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(
            horizontal = 4.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingChromeIconButton(onClick = { navigator.pop() }, floating = floating) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
        }
        Text(
            stringResource(io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res.string.reading_detail_title),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
        )
        ToolbarOneshotActions(series, book, bookMenuActions, floating, onReadClick)
    }
}

@Composable
private fun ToolbarOneshotActions(
    series: KomeliaSeries,
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    floating: Boolean,
    onReadClick: ((Boolean) -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            var expandActions by remember { mutableStateOf(false) }
            FloatingChromeIconButton(onClick = { expandActions = true }, floating = floating) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.actions_show_more))
            }
            OneshotActionsMenu(
                series = series,
                book = book,
                actions = bookMenuActions,
                expanded = expandActions,
                onDismissRequest = { expandActions = false },
                onReadClick = onReadClick,
            )
        }

    }
}
