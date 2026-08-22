package snd.komelia.ui.book

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_delete_downloaded
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_download
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_download_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.LocalBookDownloadEvents
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalOfflineAvailable
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.BookReadButton
import snd.komelia.ui.common.components.ExpandableText
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported
import snd.komelia.ui.dialogs.ConfirmationDialog
import snd.komelia.ui.dialogs.book.edit.BookEditDialog
import snd.komelia.ui.dialogs.permissions.DownloadNotificationRequestDialog
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.platform.VerticalScrollbar
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.EXPANDED
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
    Column(modifier = Modifier.fillMaxSize()) {
        if (book == null || library == null) return
        BookToolBar(
            book = book,
            bookMenuActions = bookMenuActions,
            onBackPress = onBackPress,
        )

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = layout.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(
                        horizontal = layout.pageHorizontalPadding,
                        vertical = layout.pageVerticalPadding,
                    )
                    .verticalScroll(state = scrollState),
                verticalArrangement = Arrangement.spacedBy(layout.itemSpacing),
                horizontalAlignment = Alignment.Start
            ) {
                val compact = LocalWindowWidth.current == COMPACT || LocalWindowWidth.current == MEDIUM
                if (compact) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
                    ) {
                        BookThumbnail(
                            book.id,
                            modifier = Modifier
                                .heightIn(min = 180.dp, max = 240.dp)
                                .widthIn(min = 128.dp, max = 170.dp)
                                .animateContentSize()
                        )
                        BookMainInfo(
                            book = book,
                            library = library,
                            onBookReadPress = onBookReadPress,
                            onSeriesParentSeriesPress = onParentSeriesPress,
                            onDownload = onBookDownload,
                            onDownloadDelete = onBookDownloadDelete,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(layout.gridSpacing)) {
                        BookThumbnail(
                            book.id,
                            modifier = Modifier
                                .heightIn(min = 100.dp, max = 400.dp)
                                .widthIn(min = 300.dp, max = 500.dp)
                                .animateContentSize()
                        )
                        BookMainInfo(
                            book = book,
                            library = library,
                            onBookReadPress = onBookReadPress,
                            onSeriesParentSeriesPress = onParentSeriesPress,
                            onDownload = onBookDownload,
                            onDownloadDelete = onBookDownloadDelete,
                            modifier = Modifier.weight(1f, false),
                        )
                    }
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
            VerticalScrollbar(scrollState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
fun BookToolBar(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    onBackPress: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Row(
        modifier = Modifier.padding(horizontal = layout.pageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackPress) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.navigation_back),
            )
        }
        Text(
            book.metadata.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        ToolbarBookActions(book, bookMenuActions)
    }
}

@Composable
private fun ToolbarBookActions(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
) {
    Row {
        Box {
            var expandActions by remember { mutableStateOf(false) }
            IconButton(onClick = { expandActions = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
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

        if (isAdmin && !isOffline) {
            IconButton(onClick = { showEditDialog = true }) {
                Icon(Icons.Default.Edit, null)
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
        BookInfoRow(
            book = book,
            onSeriesButtonClick = onSeriesParentSeriesPress,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val offlineAvailable = LocalOfflineAvailable.current

            if (!book.deleted && !library.unavailable) {
                if (readIsSupported(book)) {
                    BookReadButton(
                        onRead = { onBookReadPress(true) },
                        onIncognitoRead = { onBookReadPress(false) },
                    )
                }
                if (offlineAvailable && (!book.downloaded || book.isLocalFileOutdated)) {
                    DownloadButton(book, onDownload)
                }
            }
            if (offlineAvailable && book.downloaded) {
                ElevatedButton(
                    onClick = onDownloadDelete,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(stringResource(Res.string.book_delete_downloaded))
                }
            }
        }
        HorizontalDivider()
        ExpandableText(
            text = book.metadata.summary,
            style = MaterialTheme.typography.bodyMedium
        )
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
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            else -> {
                Icon(Icons.Default.Download, null)
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
