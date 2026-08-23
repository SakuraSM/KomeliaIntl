package snd.komelia.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_ISBN
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_file
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_format
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_genres
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_last_read
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_links
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_local_download_outdated
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_number_and_page_count
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_percentage_read_pages_left
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_publisher
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_read_progress
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_release_date
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_remote_unavailable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_size
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_unavailable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.DefaultDateTimeFormats.localDateTimeFormat
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.common.TagList
import snd.komelia.ui.common.components.DescriptionChips
import snd.komelia.ui.common.components.DetailMetadataRow
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.LabeledEntry.Companion.stringEntry
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.strings.enumDisplayResource
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.common.KomgaWebLink
import snd.komga.client.common.coloristRole
import snd.komga.client.common.coverRole
import snd.komga.client.common.editorRole
import snd.komga.client.common.inkerRole
import snd.komga.client.common.lettererRole
import snd.komga.client.common.pencillerRole
import snd.komga.client.common.translatorRole
import snd.komga.client.common.writerRole
import kotlin.math.roundToInt

private val authorsOrder = listOf(
    writerRole,
    pencillerRole,
    inkerRole,
    coloristRole,
    lettererRole,
    coverRole,
    editorRole,
    translatorRole
)

@Composable
fun BookInfoColumn(
    publisher: String?,
    genres: List<String>?,
    authors: List<KomgaAuthor>,
    tags: List<String>,
    links: List<KomgaWebLink>,
    sizeInMiB: String,
    mediaType: String?,
    isbn: String,
    fileUrl: String,
    onFilterClick: (SeriesScreenFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!publisher.isNullOrBlank()) {
            DescriptionChips(
                label = stringResource(Res.string.book_publisher),
                chipValue = stringEntry(publisher),
                onClick = { onFilterClick(SeriesScreenFilter(publisher = listOf(it))) },
            )
        }

        val genreEntries = remember(genres) { genres?.map { stringEntry(it) } }
        if (genreEntries != null) {
            DescriptionChips(
                label = stringResource(Res.string.book_genres),
                chipValues = genreEntries,
                onChipClick = { onFilterClick(SeriesScreenFilter(genres = listOf(it))) },
            )
        }

        TagList(
            tags = tags,
            secondaryTags = null,
            onTagClick = { onFilterClick(SeriesScreenFilter(tags = listOf(it))) },
        )

        val uriHandler = LocalUriHandler.current
        val linkEntries = remember(links) { links.map { LabeledEntry(it, it.label) } }
        DescriptionChips(
            label = stringResource(Res.string.book_links),
            chipValues = linkEntries,
            onChipClick = { entry -> uriHandler.openUri(entry.url) },
            icon = Icons.Default.Link,
        )

        Spacer(Modifier.size(0.dp))
        val authorEntries = remember(authors) {
            authors
                .groupBy { it.role }
                .toList()
                .sortedBy { (role, _) -> authorsOrder.indexOf(role.lowercase()) }
        }
        authorEntries.forEach { (role, authors) ->
            val roleLabel = enumDisplayResource(role.uppercase())?.let { stringResource(it) }
                ?: role.replaceFirstChar { it.uppercase() }
            DescriptionChips(
                label = roleLabel,
                chipValues = authors.map { LabeledEntry(it, it.name) },
                onChipClick = { onFilterClick(SeriesScreenFilter(authors = listOf(it))) },
            )
        }

        Spacer(Modifier.size(0.dp))
        DetailMetadataRow(stringResource(Res.string.book_size)) {
            SelectionContainer { Text(sizeInMiB, style = MaterialTheme.typography.labelLarge) }
        }

        DetailMetadataRow(stringResource(Res.string.book_format)) {
            if (mediaType != null) {
                val displayType = remember(mediaType) {
                    when (mediaType.lowercase()) {
                        "application/epub+zip" -> "EPUB"
                        "application/pdf" -> "PDF"
                        "application/zip", "application/x-cbz" -> "CBZ / ZIP"
                        "application/vnd.comicbook-rar", "application/x-rar-compressed" -> "CBR / RAR"
                        else -> mediaType
                    }
                }
                SelectionContainer { Text(displayType, style = MaterialTheme.typography.labelLarge) }
            }
        }

        isbn.ifBlank { null }?.let { isbn ->
            DetailMetadataRow(stringResource(Res.string.book_ISBN)) {
                SelectionContainer { Text(isbn, style = MaterialTheme.typography.labelLarge) }
            }
        }

        DetailMetadataRow(stringResource(Res.string.book_file)) {
            SelectionContainer { Text(fileUrl, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun BookInfoRow(
    modifier: Modifier = Modifier,
    book: KomeliaBook,
    onSeriesButtonClick: (() -> Unit)? = null,
) {

    Column(
        modifier = modifier,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (onSeriesButtonClick != null) {
                ElevatedButton(
                    onClick = onSeriesButtonClick,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = book.seriesTitle,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (book.deleted) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.book_unavailable)) },
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
            if (book.remoteFileUnavailable) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.book_remote_unavailable)) },
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }

            if (book.isLocalFileOutdated) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.book_local_download_outdated)) },
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
        }

        SelectionContainer {
            Text(
                text = stringResource(
                    Res.string.book_number_and_page_count, book.metadata.number, book.media.pagesCount
                )
            )
        }

        Spacer(Modifier.heightIn(5.dp))
        SelectionContainer {
            Column {
                book.metadata.releaseDate?.let {
                    DetailMetadataRow(stringResource(Res.string.book_release_date)) {
                        Text(
                            it.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                val readProgress = book.readProgress
                val pagesCount = book.media.pagesCount
                if (readProgress != null) {
                    if (!readProgress.completed) {
                        val (percentage, pagesLeft) = remember(pagesCount, readProgress) {
                            val safePageCount = pagesCount.coerceAtLeast(1)
                            val currentPage = readProgress.page.coerceIn(0, safePageCount)
                            val pagesLeft = (safePageCount - currentPage).coerceAtLeast(0)
                            val percentage = (currentPage.toFloat() / safePageCount * 100)
                                .roundToInt()
                                .coerceIn(0, 100)
                            percentage to pagesLeft
                        }

                        DetailMetadataRow(stringResource(Res.string.book_read_progress)) {
                            Text(
                                pluralStringResource(
                                    Res.plurals.book_percentage_read_pages_left,
                                    pagesLeft,
                                    percentage, pagesLeft
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    DetailMetadataRow(stringResource(Res.string.book_last_read)) {
                        val readDate = remember(readProgress) {
                            readProgress.readDate
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .format(localDateTimeFormat)
                        }
                        Text(
                            readDate,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
