package snd.komelia.ui.oneshot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_delete_downloaded
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_edit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_edit_summary
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.book.BookInfoColumn
import snd.komelia.ui.book.BookInfoRow
import snd.komelia.ui.book.DownloadButton
import snd.komelia.ui.collection.SeriesCollectionsContent
import snd.komelia.ui.common.BookReadButton
import snd.komelia.ui.common.components.ExpandableText
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.KomeliaTopBarSurface
import snd.komelia.ui.common.components.LocalNavigationBarInset
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.OneshotActionsMenu
import snd.komelia.ui.common.readIsSupported
import snd.komelia.ui.detailCoverWidth
import snd.komelia.ui.dialogs.oneshot.OneshotEditDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.readlist.BookReadListsContent
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
    cardWidth: Dp
) {
    val scrollState = rememberScrollState()
    val layout = LocalKomeliaLayout.current
    val isContentScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    PauseGlassWhileBusy(scrollState.isScrollInProgress)
    val compact = LocalWindowWidth.current == COMPACT || LocalWindowWidth.current == MEDIUM
    KomeliaPageScaffold(topBar = {
        if (compact && !isContentScrolled) {
            OneshotToolBar(series, book, oneshotMenuActions, floating = true)
        } else {
            KomeliaTopBarSurface(isContentScrolled) {
                OneshotToolBar(series, book, oneshotMenuActions)
            }
        }
    }) { topInset ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = layout.contentMaxWidth).fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = layout.pageHorizontalPadding)
                    .padding(top = topInset + layout.topBarContentSpacing,
                        bottom = LocalNavigationBarInset.current + layout.pageVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val coverWidth = detailCoverWidth(maxWidth, LocalWindowWidth.current)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing)) {
                        BookThumbnail(book.id, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(coverWidth).aspectRatio(0.703f)
                                .clip(MaterialTheme.shapes.small)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small))
                        OneshotMainInfo(series, book, library, onLibraryClick, onBookReadClick,
                            onBookDownload, onBookDownloadDelete, Modifier.weight(1f))
                    }
                }
                if (book.metadata.summary.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(Res.string.book_edit_summary), style = MaterialTheme.typography.titleLarge)
                    ExpandableText(book.metadata.summary, style = MaterialTheme.typography.bodyMedium)
                }
                SettingsSection {
                    BookInfoColumn(
                        publisher = series.metadata.publisher,
                        genres = series.metadata.genres,
                        authors = book.metadata.authors,
                        tags = book.metadata.tags,
                        links = book.metadata.links,
                        sizeInMiB = book.size,
                        mediaType = book.media.mediaType,
                        isbn = book.metadata.isbn,
                        fileUrl = book.url,
                        onFilterClick = onFilterClick,
                    )
                }
                BookReadListsContent(readLists, onReadListClick, onReadlistBookClick, cardWidth)
                SeriesCollectionsContent(collections, onCollectionClick, onSeriesClick, cardWidth)
            }
            VerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
fun OneshotToolBar(
    series: KomeliaSeries,
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    floating: Boolean = false,
) {
    val navigator = LocalNavigator.currentOrThrow
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = LocalKomeliaLayout.current.pageHorizontalPadding,
            vertical = if (floating) 8.dp else 0.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingChromeIconButton(onClick = { navigator.pop() }, floating = floating) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
        }
        Text(
            if (floating) "" else book.metadata.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        ToolbarOneshotActions(series, book, bookMenuActions, floating)
    }
}

@Composable
private fun ToolbarOneshotActions(
    series: KomeliaSeries,
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    floating: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            var expandActions by remember { mutableStateOf(false) }
            FloatingChromeIconButton(onClick = { expandActions = true }, floating = floating) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.actions_show_more))
            }
            OneshotActionsMenu(
                series = series,
                book = book,
                actions = bookMenuActions,
                expanded = expandActions,
                onDismissRequest = { expandActions = false }
            )
        }

        val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
        var showEditDialog by remember { mutableStateOf(false) }
        if (isAdmin && !book.libraryId.isLocalLibrary()) {
            FloatingChromeIconButton(onClick = { showEditDialog = true }, floating = floating) {
                Icon(Icons.Default.Edit, stringResource(Res.string.book_edit))
            }
        }
        if (showEditDialog) {
            OneshotEditDialog(
                seriesId = series.id,
                series = series,
                book = book,
                onDismissRequest = { showEditDialog = false })
        }
    }
}

@Composable
private fun OneshotMainInfo(
    series: KomeliaSeries,
    book: KomeliaBook,
    library: KomgaLibrary,
    onLibraryClick: (KomgaLibrary) -> Unit,
    onBookReadClick: (markReadProgress: Boolean) -> Unit,
    onDownload: () -> Unit,
    onDownloadDelete: () -> Unit,
    modifier: Modifier,
) {
    val isDeleted = remember(series, library) { series.deleted || library.unavailable }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(book.metadata.title, style = MaterialTheme.typography.headlineSmall,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        SeriesDescriptionRow(
            library = library,
            onLibraryClick = onLibraryClick,
            releaseDate = null,
            status = null,
            ageRating = series.metadata.ageRating,
            language = series.metadata.language,
            readingDirection = series.metadata.readingDirection,
            deleted = isDeleted,
            alternateTitles = series.metadata.alternateTitles,
            onFilterClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        BookInfoRow(
            book = book,
            onSeriesButtonClick = null,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (readIsSupported(book) && !isDeleted) {
                BookReadButton(
                    onRead = { onBookReadClick(true) },
                    onIncognitoRead = { onBookReadClick(false) }
                )
                if (!book.libraryId.isLocalLibrary() && (!book.downloaded || book.isLocalFileOutdated)) {
                    DownloadButton(book, onDownload)
                }
            }

            if (!book.libraryId.isLocalLibrary() && book.downloaded) {
                ElevatedButton(
                    onClick = onDownloadDelete,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(stringResource(Res.string.book_delete_downloaded))
                }
            }
        }

    }
}
