package snd.komelia.ui.book

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_delete_downloaded
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_download
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_download_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_edit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_progress_percent
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.enum_in_progress
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.enum_unread
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.LocalBookDownloadEvents
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineAvailable
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.BookReadButton
import snd.komelia.ui.common.cards.bookReadProgressFraction
import snd.komelia.ui.common.components.ExpandableText
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.components.KomeliaChromeSurface
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.KomeliaTopBarSurface
import snd.komelia.ui.common.components.LocalNavigationBarInset
import snd.komelia.ui.common.components.PauseGlassWhileBusy
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported
import snd.komelia.ui.detailCoverWidth
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.book.edit.BookEditDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
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
    cardWidth: Dp
) {

    val scrollState: ScrollState = rememberScrollState()
    val layout = LocalKomeliaLayout.current
    val isContentScrolled by remember(scrollState) { derivedStateOf { scrollState.value > 0 } }
    if (book == null || library == null) return
    val compact = LocalWindowWidth.current == COMPACT || LocalWindowWidth.current == MEDIUM
    val canRead = !book.deleted && !library.unavailable && readIsSupported(book)
    val density = LocalDensity.current
    var readingBarHeight by remember { mutableStateOf(72.dp) }
    var artworkUnavailable by remember(book.id) { mutableStateOf(false) }
    PauseGlassWhileBusy(scrollState.isScrollInProgress)
    KomeliaPageScaffold(topBar = {
        if (compact && !isContentScrolled) {
            BookToolBar(book, bookMenuActions, onBackPress, floating = true, showTitle = false)
        } else KomeliaTopBarSurface(isContentScrolled = isContentScrolled) {
            BookToolBar(book, bookMenuActions, onBackPress)
        }
    }, bottomBar = {
        if (compact && canRead) {
            KomeliaChromeSurface(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = layout.pageHorizontalPadding)
                    .padding(bottom = LocalNavigationBarInset.current + 8.dp)
                    .onSizeChanged { readingBarHeight = with(density) { it.height.toDp() } },
                shape = RoundedCornerShape(32.dp),
                shadowElevation = 6.dp,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progress = bookReadProgressFraction(book)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when {
                                book.readProgress == null -> stringResource(Res.string.enum_unread)
                                progress == null -> stringResource(Res.string.enum_in_progress)
                                else -> stringResource(Res.string.home_progress_percent, (progress * 100).roundToInt())
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                            drawStopIndicator = {})
                    }
                    BookReadButton(onRead = { onBookReadPress(true) }, onIncognitoRead = { onBookReadPress(false) },
                        containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }) { topInset ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = layout.contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(state = scrollState)
                    .padding(horizontal = if (compact) 0.dp else layout.pageHorizontalPadding)
                    .padding(
                        top = if (compact) 0.dp else layout.topBarContentSpacing + topInset,
                        bottom = layout.pageVerticalPadding + LocalNavigationBarInset.current + if (compact && canRead) readingBarHeight + 12.dp else 0.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
                horizontalAlignment = Alignment.Start
            ) {
                if (compact) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        BookThumbnail(book.id, contentScale = ContentScale.Crop,
                            onLoadResult = { artworkUnavailable = !it },
                            modifier = Modifier.fillMaxWidth().height(
                                if (artworkUnavailable) 136.dp else (maxWidth * 0.95f).coerceIn(300.dp, 480.dp)))
                    }
                    BookMainInfo(
                        book, library, onBookReadPress, onParentSeriesPress, onBookDownload, onBookDownloadDelete,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = layout.pageHorizontalPadding),
                        showReadAction = false,
                    )
                } else {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val coverWidth = detailCoverWidth(maxWidth, LocalWindowWidth.current)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing),
                            verticalAlignment = Alignment.Top,
                        ) {
                            BookThumbnail(
                                book.id,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .width(coverWidth)
                                    .aspectRatio(0.703f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                            )
                            BookMainInfo(
                                book = book,
                                library = library,
                                onBookReadPress = onBookReadPress,
                                onSeriesParentSeriesPress = onParentSeriesPress,
                                onDownload = onBookDownload,
                                onDownloadDelete = onBookDownloadDelete,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                }
                Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) layout.pageHorizontalPadding else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.itemSpacing)) {

                    if (book.metadata.summary.isNotBlank()) {
                        ExpandableText(
                            text = book.metadata.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    BookInfoColumn(
                        publisher = null,
                        genres = null,
                        authors = book.metadata.authors,
                        tags = book.metadata.tags,
                        links = book.metadata.links,
                        sizeInMiB = book.size,
                        mediaType = book.media.mediaType,
                        isbn = book.metadata.isbn,
                        fileUrl = book.url,
                        onFilterClick = onFilterClick,
                    )
                    BookReadListsContent(
                        readLists = readLists,
                        onReadListClick = onReadListClick,
                        onBookClick = onReadListBookPress,
                        cardWidth = cardWidth
                    )
                }
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
) {
    val layout = LocalKomeliaLayout.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = layout.pageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingChromeIconButton(onBackPress, floating) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.navigation_back),
            )
        }
        Text(
            if (showTitle) book.metadata.title else "",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        ToolbarBookActions(book, bookMenuActions, floating)
    }
}

@Composable
private fun ToolbarBookActions(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    floating: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            var expandActions by remember { mutableStateOf(false) }
            FloatingChromeIconButton(onClick = { expandActions = true }, floating = floating) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.actions_show_more))
            }
            BookActionsMenu(
                book = book,
                actions = bookMenuActions,
                expanded = expandActions,
                showEditOption = false,
                showDownloadOption = false,
                onDismissRequest = { expandActions = false }
            )
        }

        val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
        val isOffline = LocalOfflineMode.current.collectAsState().value
        var showEditDialog by remember { mutableStateOf(false) }

        if (isAdmin && !isOffline && !book.libraryId.isLocalLibrary()) {
            FloatingChromeIconButton(onClick = { showEditDialog = true }, floating = floating) {
                Icon(Icons.Rounded.Edit, stringResource(Res.string.book_edit))
            }
        }
        if (showEditDialog) {
            BookEditDialog(book = book, onDismissRequest = { showEditDialog = false })
        }
    }
}

@Composable
private fun BookMainInfo(
    book: KomeliaBook,
    library: KomgaLibrary,
    onBookReadPress: (markReadProgress: Boolean) -> Unit,
    onSeriesParentSeriesPress: () -> Unit,
    onDownload: () -> Unit,
    onDownloadDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showReadAction: Boolean = true,
) {
    val layout = LocalKomeliaLayout.current
    val maxWidth = when (LocalWindowWidth.current) {
        FULL -> 1200.dp
        else -> Dp.Unspecified
    }
    val minWidth = when (LocalWindowWidth.current) {
        COMPACT, MEDIUM -> Dp.Unspecified
        else -> 350.dp
    }

    Column(
        modifier = modifier.widthIn(min = minWidth, max = maxWidth),
        verticalArrangement = Arrangement.spacedBy(layout.itemSpacing)
    ) {
        val isEpub = book.media.mediaType == "application/epub+zip"
        Text(if (isEpub) book.metadata.title else book.seriesTitle.ifBlank { book.metadata.title },
            style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!isEpub && book.seriesTitle.isNotBlank() && book.metadata.title != book.seriesTitle) {
            Text(book.metadata.title, style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BookInfoRow(
            book = book,
            onSeriesButtonClick = onSeriesParentSeriesPress,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        ) {
            val offlineAvailable = LocalOfflineAvailable.current
            val isLocalBook = book.libraryId.isLocalLibrary()

            if (!book.deleted && !library.unavailable) {
                if (showReadAction && readIsSupported(book)) {
                    BookReadButton(
                        onRead = { onBookReadPress(true) },
                        onIncognitoRead = { onBookReadPress(false) },
                    )
                }
                if (!isLocalBook && offlineAvailable && (!book.downloaded || book.isLocalFileOutdated)) {
                    DownloadButton(book, onDownload)
                }
            }
            if (!isLocalBook && offlineAvailable && book.downloaded) {
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

@Composable
fun DownloadButton(
    book: KomeliaBook,
    onDownload: () -> Unit,
) {
    var showDownloadConfirmation by remember { mutableStateOf(false) }
    val downloadEvents = LocalBookDownloadEvents.current
    var downloadEvent: DownloadEvent? by remember { mutableStateOf(null) }
    LaunchedEffect(downloadEvents, book) {
        downloadEvents?.filter { it.bookId == book.id }?.collect { downloadEvent = it }
    }

    ElevatedButton(
        enabled = downloadEvent == null,
        onClick = { showDownloadConfirmation = true },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        elevation = null
    ) {
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
