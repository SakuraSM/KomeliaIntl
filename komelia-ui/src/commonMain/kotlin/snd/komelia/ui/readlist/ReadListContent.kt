package snd.komelia.ui.readlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.readlist_book_count
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.readlist_edit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.readlist_edit_mode_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.readlist_edit_mode_ordered_desc
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.KomeliaSafeTopBar
import snd.komelia.ui.common.components.PageSizeSelectionDropdown
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.itemlist.BookLazyCardGrid
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.common.menus.ReadListActionsMenu
import snd.komelia.ui.common.menus.bulk.BottomBulkActionsPanel
import snd.komelia.ui.common.menus.bulk.BulkActionsContainer
import snd.komelia.ui.common.menus.bulk.ReadListBulkActionsContent
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.readlist.KomgaReadList

@Composable
fun ReadListContent(
    readList: KomgaReadList,
    onReadListDelete: () -> Unit,

    books: List<KomeliaBook>,
    bookMenuActions: BookMenuActions,
    onBookClick: (KomeliaBook) -> Unit,
    onBookReadClick: (KomeliaBook, Boolean) -> Unit,

    selectedBooks: List<KomeliaBook>,
    onBookSelect: (KomeliaBook) -> Unit,

    editMode: Boolean,
    onEditModeChange: (Boolean) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onReorderDragStateChange: (dragging: Boolean) -> Unit = {},

    totalPages: Int,
    currentPage: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,

    cardMinSize: Dp,
) {
    val layout = LocalKomeliaLayout.current
    Column {
        KomeliaSafeTopBar {
            if (editMode)
                BulkActionsToolbar(
                    onCancel = { onEditModeChange(false) },
                    readList = readList,
                    books = books,
                    selectedBooks = selectedBooks,
                    onBookSelect = onBookSelect
                )
            else {
                ReadListToolbar(
                    readList = readList,
                    onReadListDelete = onReadListDelete,
                    onEditModeEnable = { onEditModeChange(true) },

                    pageSize = pageSize,
                    onPageSizeChange = onPageSizeChange,
                )
            }

        }

        if (readList.summary.isNotBlank()) {
            Column(
                Modifier.padding(
                    horizontal = layout.pageHorizontalPadding,
                    vertical = layout.controlSpacing,
                ),
                verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            ) {
                Text(readList.summary)
                HorizontalDivider()
            }
        }
        Box(Modifier.weight(1f)) {
            BookLazyCardGrid(
                books = books,
                onBookClick = if (editMode) onBookSelect else onBookClick,
                onBookReadClick = if (editMode) null else onBookReadClick,
                bookMenuActions = if (editMode) null else bookMenuActions,

                selectedBooks = selectedBooks,
                onBookSelect = onBookSelect,

                reorderable = readList.ordered && editMode,
                onReorder = onReorder,
                onReorderDragStateChange = onReorderDragStateChange,

                totalPages = totalPages,
                currentPage = currentPage,
                onPageChange = onPageChange,

                minSize = cardMinSize,
                listMode = true,
                firstItemNumber = if (editMode) 1 else (currentPage - 1) * pageSize + 1,
            )
        }

        val width = LocalWindowWidth.current
        if ((width == WindowSizeClass.COMPACT || width == WindowSizeClass.MEDIUM) && selectedBooks.isNotEmpty()) {
            BottomBulkActionsPanel {
                ReadListBulkActionsContent(readList, selectedBooks, true)
            }
        }
    }
}

@Composable
private fun ReadListToolbar(
    readList: KomgaReadList,
    onReadListDelete: () -> Unit,
    onEditModeEnable: () -> Unit,
    pageSize: Int,
    onPageSizeChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val navigator = LocalNavigator.currentOrThrow
    Column(Modifier.padding(horizontal = layout.pageHorizontalPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingChromeIconButton(onClick = { navigator.pop() }, floating = true) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
            }
            Spacer(Modifier.weight(1f))

            val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
            if (isAdmin) {
                Box {
                    var expandActions by remember { mutableStateOf(false) }
                    FloatingChromeIconButton(onClick = { expandActions = true }, floating = true) {
                        Icon(Icons.Rounded.MoreVert, stringResource(Res.string.actions_show_more))
                    }

                    ReadListActionsMenu(
                        readList = readList,
                        onReadListDelete = onReadListDelete,
                        expanded = expandActions,
                        onDismissRequest = { expandActions = false }
                    )
                }
                FloatingChromeIconButton(onClick = onEditModeEnable, floating = true) { Icon(Icons.Rounded.EditNote, stringResource(Res.string.readlist_edit)) }
            }

            PageSizeSelectionDropdown(pageSize, onPageSizeChange)
        }
        Text(readList.name, style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(pluralStringResource(Res.plurals.readlist_book_count, readList.bookIds.size, readList.bookIds.size),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp))
    }
}

@Composable
private fun BulkActionsToolbar(
    onCancel: () -> Unit,
    readList: KomgaReadList,
    books: List<KomeliaBook>,
    selectedBooks: List<KomeliaBook>,
    onBookSelect: (KomeliaBook) -> Unit,
) {
    BulkActionsContainer(
        onCancel = onCancel,
        selectedCount = selectedBooks.size,
        allSelected = books.size == selectedBooks.size,
        onSelectAll = {
            if (books.size == selectedBooks.size) books.forEach { onBookSelect(it) }
            else books.filter { it !in selectedBooks }.forEach { onBookSelect(it) }
        }
    ) {
        when (LocalWindowWidth.current) {
            WindowSizeClass.FULL -> {
                if (readList.ordered) Text(stringResource(Res.string.readlist_edit_mode_ordered_desc))
                else Text(stringResource(Res.string.readlist_edit_mode_desc))
                if (selectedBooks.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))

                    ReadListBulkActionsContent(readList, selectedBooks, false)
                }
            }

            WindowSizeClass.EXPANDED -> {
                if (selectedBooks.isEmpty()) {
                    if (readList.ordered) Text(stringResource(Res.string.readlist_edit_mode_ordered_desc))
                    else Text(stringResource(Res.string.readlist_edit_mode_desc))
                } else {
                    Spacer(Modifier.weight(1f))
                    ReadListBulkActionsContent(readList, selectedBooks, false)
                }
            }

            WindowSizeClass.COMPACT, WindowSizeClass.MEDIUM -> {}
        }
    }
}
