package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_alternative_titles
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_release_year
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_summary_from_book
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_unavailable
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.KomeliaSpacing
import snd.komelia.ui.common.components.ExpandableText
import snd.komelia.ui.common.components.DetailMetadataRow
import snd.komelia.ui.library.SeriesScreenFilter
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.common.KomgaReadingDirection
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.series.KomgaAlternativeTitle
import snd.komga.client.series.KomgaSeriesStatus
import snd.komga.client.series.KomgaSeriesStatus.ABANDONED
import snd.komga.client.series.KomgaSeriesStatus.ENDED
import snd.komga.client.series.KomgaSeriesStatus.HIATUS
import snd.komga.client.series.KomgaSeriesStatus.ONGOING

@Composable
fun SeriesDescriptionRow(
    library: KomgaLibrary,
    onLibraryClick: (KomgaLibrary) -> Unit,
    releaseDate: LocalDate?,
    status: KomgaSeriesStatus?,
    ageRating: Int?,
    language: String,
    readingDirection: KomgaReadingDirection?,
    deleted: Boolean,
    alternateTitles: List<KomgaAlternativeTitle>,
    onFilterClick: (SeriesScreenFilter) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.Start
    ) {

        if (releaseDate != null) {
            Text(
                text = stringResource(Res.string.series_release_year, releaseDate.year.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ElevatedButton(
                onClick = { onLibraryClick(library) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
            ) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                Spacer(Modifier.width(3.dp))
                Text(text = library.name, textDecoration = TextDecoration.Underline)
            }

            if (status != null) {
                SuggestionChip(
                    onClick = { onFilterClick(SeriesScreenFilter(publicationStatus = listOf(status))) },
                    label = { Text(stringResource(AppStrings.forSeriesStatus(status))) },
                    border = null,
                    colors =
                        when (status) {
                            ENDED -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                labelColor = MaterialTheme.colorScheme.onSecondary
                            )

                            ONGOING -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            ABANDONED -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                            )

                            HIATUS -> SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        },
                )
            }

            ageRating?.let { age ->
                SuggestionChip(
                    onClick = { onFilterClick(SeriesScreenFilter(ageRating = listOf(age))) },
                    label = { Text("$age+") }
                )
            }

            if (language.isNotBlank())
                SuggestionChip(
                    onClick = { onFilterClick(SeriesScreenFilter(language = listOf(language))) },
                    label = { Text(language) }
                )

            if (readingDirection != null) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(AppStrings.forReadingDirection(readingDirection))) }
                )
            }

            if (deleted) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(stringResource(Res.string.series_unavailable)) },
                    border = null,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }
        }

        if (alternateTitles.isNotEmpty()) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(KomeliaSpacing.extraSmall)) {
                    Text(
                        text = stringResource(Res.string.series_alternative_titles),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    alternateTitles.forEach {
                        DetailMetadataRow(label = it.label) {
                            Text(
                                text = it.title,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesSummary(
    seriesSummary: String,
    bookSummary: String,
    bookSummaryNumber: String,
) {
    val localizedBookSummary = if (bookSummary.isBlank()) {
        null
    } else {
        stringResource(Res.string.series_summary_from_book, bookSummaryNumber, bookSummary)
    }
    val summaryText = remember(seriesSummary, localizedBookSummary) {
        seriesSummary.ifBlank { localizedBookSummary }
    }
    if (summaryText != null) {
        ExpandableText(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
