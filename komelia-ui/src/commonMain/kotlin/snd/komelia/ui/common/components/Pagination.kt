package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_first_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_last_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_more_pages
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_next_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_page
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_page_status
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.pagination_previous_page
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.components.LabeledEntry.Companion.intEntry

internal sealed interface PaginationItem {
    data class Page(val number: Int) : PaginationItem
    data object Ellipsis : PaginationItem
}

internal fun paginationItems(
    totalPages: Int,
    currentPage: Int,
    maxSlots: Int,
): List<PaginationItem> {
    if (totalPages <= 0) return emptyList()
    val current = currentPage.coerceIn(1, totalPages)
    val slots = maxSlots.coerceAtLeast(5)
    if (totalPages <= slots) return (1..totalPages).map(PaginationItem::Page)

    val pageNumbers = when {
        current <= slots - 3 -> 1..(slots - 2)
        current >= totalPages - (slots - 4) -> (totalPages - (slots - 3))..totalPages
        else -> {
            val middleSlots = slots - 4
            val start = current - middleSlots / 2
            start..(start + middleSlots - 1)
        }
    }

    return buildList {
        add(PaginationItem.Page(1))
        if (pageNumbers.first > 2) add(PaginationItem.Ellipsis)
        pageNumbers.filter { it != 1 && it != totalPages }.forEach { add(PaginationItem.Page(it)) }
        if (pageNumbers.last < totalPages - 1) add(PaginationItem.Ellipsis)
        add(PaginationItem.Page(totalPages))
    }
}

@Composable
fun Pagination(
    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    navigationButtons: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 1) {
        Box(modifier)
        return
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val current = currentPage.coerceIn(1, totalPages)
        if (maxWidth < 420.dp && navigationButtons) {
            CompactPagination(
                totalPages = totalPages,
                currentPage = current,
                onPageChange = onPageChange,
            )
        } else {
            val maxSlots = when {
                maxWidth < 560.dp -> 5
                maxWidth < 800.dp -> 7
                else -> 9
            }
            NumberedPagination(
                items = remember(totalPages, current, maxSlots) {
                    paginationItems(totalPages, current, maxSlots)
                },
                totalPages = totalPages,
                currentPage = current,
                navigationButtons = navigationButtons,
                onPageChange = onPageChange,
            )
        }
    }
}

@Composable
private fun CompactPagination(
    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaginationNavigationButton(
            enabled = currentPage > 1,
            onClick = { onPageChange(1) },
            icon = { Icon(Icons.Rounded.FirstPage, contentDescription = null) },
            contentDescription = stringResource(Res.string.pagination_first_page),
        )
        PaginationNavigationButton(
            enabled = currentPage > 1,
            onClick = { onPageChange(currentPage - 1) },
            icon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
            contentDescription = stringResource(Res.string.pagination_previous_page),
        )
        val status = stringResource(Res.string.pagination_page_status, currentPage, totalPages)
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .heightIn(min = layout.minimumTouchTarget)
                .widthIn(min = 72.dp)
                .semantics { contentDescription = status },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
        PaginationNavigationButton(
            enabled = currentPage < totalPages,
            onClick = { onPageChange(currentPage + 1) },
            icon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
            contentDescription = stringResource(Res.string.pagination_next_page),
        )
        PaginationNavigationButton(
            enabled = currentPage < totalPages,
            onClick = { onPageChange(totalPages) },
            icon = { Icon(Icons.AutoMirrored.Rounded.LastPage, contentDescription = null) },
            contentDescription = stringResource(Res.string.pagination_last_page),
        )
    }
}

@Composable
private fun NumberedPagination(
    items: List<PaginationItem>,
    totalPages: Int,
    currentPage: Int,
    navigationButtons: Boolean,
    onPageChange: (Int) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationButtons) {
            PaginationNavigationButton(
                enabled = currentPage > 1,
                onClick = { onPageChange(currentPage - 1) },
                icon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) },
                contentDescription = stringResource(Res.string.pagination_previous_page),
            )
        }

        val morePagesDescription = stringResource(Res.string.pagination_more_pages)
        items.forEach { item ->
            when (item) {
                is PaginationItem.Page -> PageNumberButton(item.number, currentPage, onPageChange)
                PaginationItem.Ellipsis -> Text(
                    text = "…",
                    modifier = Modifier
                        .width(layout.minimumTouchTarget / 2)
                        .semantics { contentDescription = morePagesDescription },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        if (navigationButtons) {
            PaginationNavigationButton(
                enabled = currentPage < totalPages,
                onClick = { onPageChange(currentPage + 1) },
                icon = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                contentDescription = stringResource(Res.string.pagination_next_page),
            )
        }
    }
}

@Composable
private fun PaginationNavigationButton(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    contentDescription: String,
) {
    val layout = LocalKomeliaLayout.current
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .size(layout.minimumTouchTarget)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { this.contentDescription = contentDescription },
        content = icon,
    )
}

@Composable
private fun PageNumberButton(
    pageNumber: Int,
    currentPage: Int,
    onClick: (Int) -> Unit
) {
    val layout = LocalKomeliaLayout.current
    val isCurrent = pageNumber == currentPage
    val pageDescription = stringResource(Res.string.pagination_page, pageNumber)
    IconButton(
        enabled = !isCurrent,
        onClick = { onClick(pageNumber) },
        colors = IconButtonDefaults.iconButtonColors(
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier
            .size(layout.minimumTouchTarget)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics {
                contentDescription = pageDescription
                selected = isCurrent
            },
    ) {
        Text(pageNumber.toString())
    }
}

@Composable
fun PageSizeSelectionDropdown(
    currentSize: Int,
    onPageSizeChange: (Int) -> Unit
) {
    DropdownChoiceMenu(
        selectedOption = intEntry(currentSize),
        options = listOf(
            intEntry(20),
            intEntry(50),
            intEntry(100),
            intEntry(200),
            intEntry(500)
        ),
        onOptionChange = { onPageSizeChange(it.value) },
        contentPadding = PaddingValues(5.dp),
        inputFieldColor = MaterialTheme.colorScheme.surface,
        inputFieldModifier = Modifier
            .widthIn(min = 70.dp)
            .clip(RoundedCornerShape(8.dp))
            .padding(end = 10.dp)
    )
}
