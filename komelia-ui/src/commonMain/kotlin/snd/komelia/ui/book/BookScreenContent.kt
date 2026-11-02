package snd.komelia.ui.book

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_file_source
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_summary
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_series_info
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_series_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_detail_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_download
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_download_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.LocalBookDownloadEvents
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.ReadingDesign
import snd.komelia.offline.local.isLocalLibrary
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_source
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.List
import snd.komelia.ui.common.components.ExpandableText
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.LocalNavigationBarInset
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.readlist.BookReadListsContent
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.readlist.KomgaReadList

@Composable
fun BookScreenContent(
    library: KomgaLibrary?,
    book: KomeliaBook?,
    bookMenuActions: BookMenuActions,
    onBookReadPress: (markReadProgress: Boolean) -> Unit,
    onBookDownload: () -> Unit,
    onBookDownloadDelete: () -> Unit,
    readLists: Map<KomgaReadList, List<KomeliaBook>>,
    onReadListClick: (KomgaReadList) -> Unit,
    onReadListBookPress: (KomeliaBook, KomgaReadList) -> Unit,
    onParentSeriesPress: () -> Unit,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    onBackPress: () -> Unit,
    cardWidth: Dp,
    onContents: (() -> Unit)? = null,
    readingNavigation: @Composable () -> Unit = {},
    extraDetails: @Composable () -> Unit = {},
    customToolbar: (@Composable (Boolean, ((Boolean) -> Unit)?) -> Unit)? = null,
    publisher: String? = null,
    genres: List<String>? = null,
    readingContext: snd.komelia.ui.BookSiblingsContext = snd.komelia.ui.BookSiblingsContext.Series,
) {
    if (book == null || library == null) return
    val layout = LocalKomeliaLayout.current
    val scrollState = rememberScrollState()
    val isContentScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    val contentIsAvailable = snd.komelia.ui.common.bookContentIsAvailable(book, library.unavailable)
    val canRead = contentIsAvailable && readIsSupported(book)
    val onRead = if (canRead) onBookReadPress else null
    val windowWidth = snd.komelia.ui.LocalWindowWidth.current
    val actionInset = when (windowWidth) {
        snd.komelia.ui.platform.WindowSizeClass.COMPACT, snd.komelia.ui.platform.WindowSizeClass.MEDIUM -> 0.dp
        snd.komelia.ui.platform.WindowSizeClass.EXPANDED -> 27.dp
        snd.komelia.ui.platform.WindowSizeClass.FULL -> 30.dp
    }
    val density = LocalDensity.current
    var readingBarHeight by remember { mutableStateOf(116.dp) }
    var showFileDetails by rememberSaveable(book.id.value) { mutableStateOf(false) }
    var showContents by rememberSaveable(book.id.value) { mutableStateOf(false) }
    val navigationScope = androidx.compose.runtime.rememberCoroutineScope()
    val factory = snd.komelia.ui.LocalViewModelFactory.current
    val navigation = remember(book.id) { factory.getBookReadingNavigationState(book, navigationScope) }
    LaunchedEffect(navigation) { navigation.load() }
    PauseGlassWhileBusy(scrollState.isScrollInProgress)
    KomeliaPageScaffold(
        topBar = { customToolbar?.invoke(isContentScrolled, onRead)
            ?: BookToolBar(book, bookMenuActions, onBackPress, showTitle = isContentScrolled, onReadClick = onRead,
                onParentSeriesClick = onParentSeriesPress) },
        bottomBar = {
            if (canRead) ReadingActionFooter(book, onBookReadPress,
                Modifier.padding(bottom = LocalNavigationBarInset.current)
                    .onSizeChanged { readingBarHeight = with(density) { it.height.toDp() } }, horizontalInset = actionInset)
        },
    ) { topInset ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = layout.contentMaxWidth).fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (windowWidth == snd.komelia.ui.platform.WindowSizeClass.FULL) 40.dp
                        else if (actionInset == 0.dp && book.media.mediaType == "application/epub+zip") 20.dp else ReadingDesign.homeInset)
                    .padding(top = topInset + if (actionInset == 0.dp) {
                        if (book.media.mediaType == "application/epub+zip") 12.dp else 6.dp
                    } else if (windowWidth == snd.komelia.ui.platform.WindowSizeClass.FULL) 20.dp else 58.dp,
                        bottom = if (canRead) readingBarHeight + 16.dp else layout.gridBottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ReadingBookHeader(book, onParentSeries = if (book.oneshot) null else onParentSeriesPress,
                    unavailable = !contentIsAvailable, onDownload = onBookDownload, onContents = onContents ?: if (book.media.mediaType == "application/epub+zip") { { showContents = true } } else null)
                val wideImage = actionInset > 0.dp && book.media.mediaType != "application/epub+zip"
                if (!wideImage) HorizontalDivider(Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                if (book.metadata.summary.isNotBlank()) {
                    Column(Modifier.then(if (wideImage) Modifier.padding(top = 24.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!wideImage) Text(stringResource(Res.string.reading_summary), style = MaterialTheme.typography.titleMedium)
                        ExpandableText(book.metadata.summary, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                if (wideImage && !book.oneshot) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = onParentSeriesPress, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        contentPadding = PaddingValues(0.dp), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(Res.string.reading_series_info), style = MaterialTheme.typography.titleMedium)
                            Text(book.seriesTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
                    }
                } else BookReadingNavigation(book, readingContext, navigation, showContents, onShowContents = { showContents = it })
                readingNavigation()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = { showFileDetails = !showFileDetails },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.reading_file_source), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Rounded.ExpandMore, null)
                }
                Text(listOf(book.size, if (book.libraryId.isLocalLibrary() || book.downloaded) stringResource(Res.string.local_source) else library.name).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showFileDetails) {
                    BookInfoRow(book = book, showReadProgress = false)
                    BookInfoColumn(publisher = publisher, genres = genres, authors = book.metadata.authors,
                        tags = book.metadata.tags, links = book.metadata.links, sizeInMiB = book.size,
                        mediaType = book.media.mediaType, isbn = book.metadata.isbn,
                        fileUrl = book.url, onFilterClick = onFilterClick)
                    extraDetails()
                }
                if (readLists.isNotEmpty()) BookReadListsContent(readLists, onReadListClick, onReadListBookPress, cardWidth)
            }
            VerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
fun BookToolBar(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    onBackPress: () -> Unit,
    floating: Boolean = false,
    showTitle: Boolean = true,
    onReadClick: ((Boolean) -> Unit)? = null,
    onParentSeriesClick: (() -> Unit)? = null,
) {
    val compactToolbar = snd.komelia.ui.LocalWindowWidth.current in listOf(
        snd.komelia.ui.platform.WindowSizeClass.COMPACT, snd.komelia.ui.platform.WindowSizeClass.MEDIUM)
    val textBook = book.media.mediaType == "application/epub+zip"
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (snd.komelia.ui.LocalWindowWidth.current == snd.komelia.ui.platform.WindowSizeClass.FULL) 24.dp else 0.dp)
            .heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingChromeIconButton(onBackPress, floating) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.navigation_back),
                modifier = if (compactToolbar) Modifier.size(20.dp).offset(x = if (textBook) (-6).dp else 4.dp, y = (-4).dp)
                    .testTag("detail-toolbar-back-paint") else Modifier,
            )
        }
        Text(
            if (showTitle) book.metadata.title else if (book.media.mediaType == "application/epub+zip") stringResource(Res.string.reading_detail_title)
                else book.seriesTitle.ifBlank { book.metadata.title },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).then(if (compactToolbar) Modifier.offset(x = if (textBook) 0.dp else (-6).dp, y = (-4).dp) else Modifier),
            textAlign = if (textBook) TextAlign.Center else TextAlign.Start,
        )
        if (onParentSeriesClick != null && !book.oneshot && book.media.mediaType != "application/epub+zip" &&
            snd.komelia.ui.LocalWindowWidth.current in listOf(snd.komelia.ui.platform.WindowSizeClass.COMPACT, snd.komelia.ui.platform.WindowSizeClass.MEDIUM)) {
            FloatingChromeIconButton(onParentSeriesClick, floating) {
                Icon(Icons.AutoMirrored.Rounded.List, stringResource(Res.string.reading_series_title),
                    Modifier.size(20.dp).offset(x = 4.dp, y = (-4).dp))
            }
        }
        ToolbarBookActions(book, bookMenuActions, floating, onReadClick)
    }
}

@Composable
private fun ToolbarBookActions(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    floating: Boolean,
    onReadClick: ((Boolean) -> Unit)?,
) {
    val compact = snd.komelia.ui.LocalWindowWidth.current in listOf(
        snd.komelia.ui.platform.WindowSizeClass.COMPACT, snd.komelia.ui.platform.WindowSizeClass.MEDIUM)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            var expandActions by remember { mutableStateOf(false) }
            FloatingChromeIconButton(onClick = { expandActions = true }, floating = floating) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.actions_show_more),
                    modifier = if (compact) Modifier.size(20.dp).offset(x = 4.dp, y = (-4).dp)
                        .testTag("detail-toolbar-more-paint") else Modifier)
            }
            BookActionsMenu(
                book = book,
                actions = bookMenuActions,
                expanded = expandActions,
                showEditOption = true,
                showDownloadOption = true,
                onDismissRequest = { expandActions = false },
                onReadClick = onReadClick,
            )
        }

    }
}

@Composable
fun DownloadButton(
    book: KomeliaBook,
    onDownload: () -> Unit,
    compact: Boolean = false,
) {
    var showDownloadConfirmation by remember { mutableStateOf(false) }
    val downloadEvents = LocalBookDownloadEvents.current
    var downloadEvent: DownloadEvent? by remember { mutableStateOf(null) }
    LaunchedEffect(downloadEvents, book) {
        downloadEvents?.filter { it.bookId == book.id }?.collect { downloadEvent = it }
    }

    val statusIcon: @Composable () -> Unit = {
        when (val event = downloadEvent) {
            is DownloadEvent.BookDownloadProgress -> {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { event.completed / event.total.toFloat() },
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            else -> {
                Icon(Icons.Rounded.Download, null)
            }
        }
    }
    val downloadLabel = stringResource(Res.string.book_download)
    if (compact) Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedIconButton(enabled = downloadEvent == null, onClick = { showDownloadConfirmation = true },
            shape = CircleShape, modifier = Modifier.size(48.dp).padding(2.dp)) {
            Box(Modifier.semantics { contentDescription = downloadLabel }) { statusIcon() }
        }
        Text(stringResource(Res.string.book_download), style = MaterialTheme.typography.labelSmall)
    } else ElevatedButton(enabled = downloadEvent == null, onClick = { showDownloadConfirmation = true },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), elevation = null) {
        statusIcon()
        Spacer(Modifier.width(3.dp))
        Text(stringResource(Res.string.book_download))
    }

    if (showDownloadConfirmation) {
        var permissionRequested by remember { mutableStateOf(false) }
        DownloadNotificationRequestDialog { permissionRequested = true }

        if (permissionRequested) {
            ConfirmationDialog(
                body = stringResource(Res.string.book_download_confirm, book.name),
                onDialogConfirm = onDownload,
                onDialogDismiss = { showDownloadConfirmation = false }
            )
        }
    }

}
