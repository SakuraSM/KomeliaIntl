package snd.komelia.ui.common.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_downloaded_outdated_status
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_downloaded_status
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_unavailable
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.ui.LocalBookDownloadEvents
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.common.BookReadButton
import snd.komelia.ui.common.images.BookThumbnail
import snd.komelia.ui.common.menus.BookActionsMenu
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.readIsSupported
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.home_progress_percent
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.enum_unread
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.enum_in_progress
import kotlin.math.roundToInt

@Composable
fun BookImageCard(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions? = null,
    onBookClick: (() -> Unit)? = null,
    onBookReadClick: ((markProgress: Boolean) -> Unit)? = null,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    showSeriesTitle: Boolean = false,
    isActive: Boolean = false,
    showVolumeTitle: Boolean = false,
    compactCaption: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val libraries = LocalLibraries.current
    val libraryIsDeleted = remember {
        libraries.value.firstOrNull { it.id == book.libraryId }?.unavailable ?: false
    }
    ItemCard(
        isHighlighted = isActive,
        coverAspectRatio = if (compactCaption) .635f else if (showVolumeTitle) .66f else 2f / 3f,
        modifier = modifier,
        onClick = onBookClick,
        onLongClick = onSelect,
        image = {
            Box {
                BookHoverOverlay(
                    book = book,
                    libraryIsDeleted = libraryIsDeleted,
                    bookMenuActions = bookMenuActions,
                    onBookReadClick = onBookReadClick,
                    onSelect = onSelect,
                    isSelected = isSelected,
                ) {
                    BookImageOverlay(
                        book = book,
                        libraryIsDeleted = libraryIsDeleted,
                        showTitle = false,
                        showProgress = false,
                    ) {
                        BookThumbnail(
                            book.id,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        },
        content = {
            val progress = bookReadProgressFraction(book)
            ReadingCoverCaption(
                prominent = showVolumeTitle,
                compact = compactCaption,
                title = if (showVolumeTitle) book.seriesTitle.ifBlank { book.metadata.title } else book.metadata.title,
                progress = progress,
                status = when {
                    book.readProgress == null -> stringResource(Res.string.enum_unread)
                    progress == null -> stringResource(Res.string.enum_in_progress)
                    else -> stringResource(Res.string.home_progress_percent, (progress * 100).roundToInt())
                },
                supportingText = if (showVolumeTitle && book.seriesTitle != book.metadata.title) book.metadata.title
                    else if (showSeriesTitle && !book.oneshot) book.seriesTitle else null,
            )
        },
    )
}

@Composable
fun BookSimpleImageCard(
    book: KomeliaBook,
    onBookClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ItemCard(
        modifier = modifier,
        onClick = onBookClick,
        image = {
            BookImageOverlay(
                book = book,
                libraryIsDeleted = false,
                showTitle = false
            ) {
                BookThumbnail(
                    book.id,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    )
}

@Composable
private fun BookImageOverlay(
    book: KomeliaBook,
    libraryIsDeleted: Boolean,
    showTitle: Boolean = true,
    showSeriesTitle: Boolean = false,
    showProgress: Boolean = true,
    content: @Composable () -> Unit
) {
    val layout = LocalKomeliaLayout.current
    Box(contentAlignment = Alignment.TopStart) {
        content()
        if (book.libraryId.isLocalLibrary()) {
            LocalSourceBadge(Modifier.padding(6.dp))
        }
        if (showTitle)
            CardGradientOverlay()
        Column {
            Row {
                if (book.downloaded && !book.libraryId.isLocalLibrary()) {
                    DownloadedBookBadge(
                        needsUpdate = book.isLocalFileOutdated || book.remoteFileUnavailable,
                        modifier = Modifier.padding(6.dp),
                    )
                }

                Spacer(Modifier.weight(1f))
                if (book.readProgress == null) BookUnreadTick()
            }

            Spacer(modifier = Modifier.weight(1f))
            Column(Modifier.padding(layout.cardContentPadding)) {
                if (showSeriesTitle && !book.oneshot) {
                    CardOutlinedText(
                        text = book.seriesTitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(color = Color(195, 195, 195)),
                    )
                }
                if (showTitle) {
                    CardOutlinedText(
                        text = book.metadata.title,
                        maxLines = 2
                    )
                }
                if (!snd.komelia.ui.common.bookContentIsAvailable(book, libraryIsDeleted)) {
                    CardStatusBadge(stringResource(Res.string.book_unavailable))
                }
            }

            val readProgress = book.readProgress
            val fraction = bookReadProgressFraction(book)
            if (showProgress && readProgress != null && !readProgress.completed && fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    modifier = Modifier.height(4.dp).fillMaxWidth().background(Color.Black),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
        BookDownloadCardOverlay(book)

    }
}

@Composable
fun DownloadedBookBadge(
    needsUpdate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(30.dp),
        shape = MaterialTheme.shapes.small,
        color = if (needsUpdate) colorScheme.errorContainer else colorScheme.secondaryContainer,
        contentColor = if (needsUpdate) colorScheme.onErrorContainer else colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (needsUpdate) Icons.Rounded.SyncProblem else Icons.Rounded.DownloadDone,
                contentDescription = stringResource(
                    if (needsUpdate) Res.string.book_downloaded_outdated_status else Res.string.book_downloaded_status,
                ),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun BookDownloadCardOverlay(book: KomeliaBook) {
    val downloadEvents = LocalBookDownloadEvents.current
    var downloadEvent: DownloadEvent? by remember { mutableStateOf(null) }
    LaunchedEffect(downloadEvents, book) {
        downloadEvents?.filter { it.bookId == book.id }?.collect { downloadEvent = it }
    }

    when (val event = downloadEvent) {
        is DownloadEvent.BookDownloadCompleted -> {}
        is DownloadEvent.BookDownloadError -> {}
        is DownloadEvent.BookDownloadProgress -> {

            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary)
                CircularProgressIndicator(
                    progress = { event.completed / event.total.toFloat() },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                )
            }
        }

        null -> {}
    }

}

@Composable
private fun BookUnreadTick() {
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = Modifier.size(30.dp)) {
        val trianglePath = Path().apply {
            moveTo(0f, 0f)
            lineTo(x = size.width, y = size.height)
            lineTo(x = size.width, y = size.height)
            lineTo(x = size.width, y = 0f)
        }

        drawPath(
            color = color,
            path = trianglePath
        )
    }
}

@Composable
private fun BookHoverOverlay(
    book: KomeliaBook,
    libraryIsDeleted: Boolean,
    bookMenuActions: BookMenuActions?,
    onBookReadClick: ((Boolean) -> Unit)?,
    isSelected: Boolean,
    onSelect: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    var isActionsMenuExpanded by remember { mutableStateOf(false) }
    var isReadButtonExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    val showOverlay = derivedStateOf { isHovered.value || isActionsMenuExpanded || isReadButtonExpanded || isSelected }

    val border =
        if (showOverlay.value) overlayBorderModifier() else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .then(border),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (showOverlay.value) {
            val backgroundColor =
                if (isSelected)
                    Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = .5f))
                else Modifier
            Column(backgroundColor.fillMaxSize()) {
                if (onSelect != null) {
                    SelectionRadioButton(
                        isSelected,
                        onSelect
                    )
                    Spacer(Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.padding(vertical = 5.dp).fillMaxSize(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (onBookReadClick != null && !book.deleted && readIsSupported(book) && !libraryIsDeleted) {
                        BookReadButton(
                            modifier = Modifier.padding(start = 5.dp, bottom = 5.dp),
                            onRead = { onBookReadClick(true) },
                            onIncognitoRead = { onBookReadClick(false) },
                            onDropdownOpenChange = { isReadButtonExpanded = it }
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    if (bookMenuActions != null)
                        BookMenuActionsDropdown(
                            book = book,
                            bookMenuActions = bookMenuActions,
                            isActionsMenuExpanded = isActionsMenuExpanded,
                            onActionsMenuExpand = { isActionsMenuExpanded = it }
                        )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookDetailedListCard(
    book: KomeliaBook,
    onClick: (() -> Unit)? = null,
    bookMenuActions: BookMenuActions? = null,
    onBookReadClick: ((Boolean) -> Unit)? = null,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isActive: Boolean = false,
    compactCoverSlotWidth: Dp = 80.dp,
    searchResult: Boolean = false,
    showTrailingAction: Boolean = true,
) {
    CompactBookRow(book, onClick, bookMenuActions, onBookReadClick, isSelected, onSelect, compact, modifier,
        isActive, compactCoverSlotWidth, searchResult, showTrailingAction)
}

@Composable
private fun BookMenuActionsDropdown(
    book: KomeliaBook,
    bookMenuActions: BookMenuActions,
    isActionsMenuExpanded: Boolean,
    onActionsMenuExpand: (Boolean) -> Unit
) {
    Box {
        IconButton(
            onClick = { onActionsMenuExpand(true) },
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Icon(Icons.Rounded.MoreVert, stringResource(Res.string.actions_show_more))
        }

        BookActionsMenu(
            book = book,
            actions = bookMenuActions,
            expanded = isActionsMenuExpanded,
            showEditOption = true,
            showDownloadOption = true,
            onDismissRequest = { onActionsMenuExpand(false) },
        )
    }
}
