package snd.komelia.ui.series.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults.textButtonColors
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.text.style.TextOverflow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_advanced_filter_summary
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_show_advanced_filters
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_hide_advanced_filters
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.filter_search
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_age_rating
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_authors
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_complete
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_filter_tags_label
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_hide_filters
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_language
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_oneshot
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_publication_status
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_publisher
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_read_status
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_release_date
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_reset_filters
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_filter_sort
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.FilterDropdownChoice
import snd.komelia.ui.common.components.FilterDropdownMultiChoice
import snd.komelia.ui.common.components.FilterDropdownMultiChoiceWithSearch
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.LabeledEntry.Companion.stringEntry
import snd.komelia.ui.common.components.NoPaddingTextField
import snd.komelia.ui.common.components.TagFiltersDropdownMenu
import snd.komelia.ui.library.LibrarySeriesTabState
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.platform.WindowSizeClass.EXPANDED
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.platform.WindowSizeClass.MEDIUM
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.series.SeriesFilterState
import snd.komelia.ui.series.SeriesFilterState.Completion.ANY
import snd.komelia.ui.series.SeriesFilterState.Completion.COMPLETE
import snd.komelia.ui.series.SeriesFilterState.Completion.INCOMPLETE
import snd.komelia.ui.series.SeriesFilterState.Format
import snd.komelia.ui.strings.AppStrings
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.series.KomgaSeriesStatus

@Composable
fun SeriesFilterContent(
    filterState: SeriesFilterState,
    onDismiss: () -> Unit,
    compact: Boolean = false,
    displayOptions: @Composable () -> Unit = {},
) {
    val widthClass = LocalWindowWidth.current

    val spacing = remember(widthClass) {
        when (widthClass) {
            COMPACT, MEDIUM, EXPANDED -> 10.dp
            FULL -> 20.dp
        }
    }
    val width = remember(widthClass) {
        when (widthClass) {
            COMPACT -> 0.dp
            MEDIUM -> 220.dp
            else -> 250.dp
        }
    }
    val controlHeight = if (widthClass == COMPACT) 48.dp else 40.dp
    val filterItemModifier = if (widthClass == COMPACT) Modifier.fillMaxWidth() else Modifier.width(width)
    val currentFilter = filterState.state.collectAsState().value
    var showAdvanced by remember { mutableStateOf(!compact || filterState.isChanged) }

    Column(
        modifier = Modifier.widthIn(max = 1400.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        if (!compact || showAdvanced) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.Center,
        ) {

            var searchTerm by remember { mutableStateOf(currentFilter.searchTerm) }
            LaunchedEffect(searchTerm) {
                delay(200)
                filterState.onSearchTermChange(searchTerm)
            }
            if (!compact) NoPaddingTextField(
                text = searchTerm,
                placeholder = stringResource(Res.string.filter_search),
                onTextChange = { searchTerm = it },
                modifier = Modifier.weight(1f).height(controlHeight).widthIn(min = if (widthClass == COMPACT) 160.dp else 340.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = filterState::reset,
                    enabled = filterState.isChanged,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (filterState.isChanged) MaterialTheme.colorScheme.tertiaryContainer else Color.Unspecified,
                    ),
                    border = if (filterState.isChanged) null else ButtonDefaults.outlinedButtonBorder(true),
                    modifier = Modifier.height(controlHeight).cursorForHand()
                ) {
                    Text(
                        stringResource(Res.string.series_filter_reset_filters),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (!compact) OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(controlHeight).cursorForHand()
                ) {
                    Text(
                        stringResource(Res.string.series_filter_hide_filters),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            SeriesFilterField(stringResource(Res.string.series_filter_sort), compact, filterItemModifier) { fieldModifier ->
            FilterDropdownChoice(
                selectedOption = LabeledEntry(
                    currentFilter.sortOrder,

                    stringResource(AppStrings.forSeriesSort(currentFilter.sortOrder)),

                    ),
                options = LibrarySeriesTabState.SeriesSort.entries.map {
                    LabeledEntry(
                        it,
                        stringResource(AppStrings.forSeriesSort(it))
                    )
                },
                onOptionChange = { filterState.onSortOrderChange(it.value) },
                label = if (compact) null else stringResource(Res.string.series_filter_sort),
                modifier = fieldModifier
            )
            }
            SeriesFilterField(stringResource(Res.string.series_filter_read_status), compact, filterItemModifier) { fieldModifier ->
            FilterDropdownMultiChoice(
                selectedOptions = currentFilter.readStatus
                    .map {
                        LabeledEntry(
                            it,
                            stringResource(AppStrings.forSeriesReadStatus(it))
                        )
                    },
                options = KomgaReadStatus.entries.map {
                    LabeledEntry(
                        it,
                        stringResource(AppStrings.forSeriesReadStatus(it))
                    )
                },
                onOptionSelect = { changed -> filterState.onReadStatusSelect(changed.value) },
                label = if (compact) null else stringResource(Res.string.series_filter_read_status),
                modifier = fieldModifier,
            )
            }

            displayOptions()
            if (compact) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    contentPadding = PaddingValues(0.dp), colors = textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(if (showAdvanced) Res.string.browse_hide_advanced_filters else Res.string.browse_show_advanced_filters),
                        style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(Res.string.browse_advanced_filter_summary), Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight, null)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (!compact || showAdvanced) {
            TagFiltersDropdownMenu(
                allTags = filterState.tagOptions,
                includeTags = currentFilter.includeTags,
                excludeTags = currentFilter.excludeTags,
                onTagSelect = filterState::onTagSelect,

                allGenres = filterState.genresOptions,
                includeGenres = currentFilter.includeGenres,
                excludeGenres = currentFilter.excludeGenres,
                onGenreSelect = filterState::onGenreSelect,

                onReset = filterState::resetTagFilters,
                inclusionMode = currentFilter.inclusionMode,
                onInclusionModeChange = filterState::onInclusionModeChange,
                exclusionMode = currentFilter.exclusionMode,
                onExclusionModeChange = filterState::onExclusionModeChange,

                contentPadding = PaddingValues(5.dp),
                label = stringResource(Res.string.series_filter_filter_tags_label),
                inputFieldColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .then(filterItemModifier)
                    .clip(RoundedCornerShape(5.dp)),
                inputFieldModifier = Modifier.fillMaxWidth()
            )
            FilterDropdownMultiChoice(
                selectedOptions = currentFilter.publicationStatus
                    .map {
                        LabeledEntry(
                            it,
                            stringResource(AppStrings.forPublicationStatus(it))

                        )
                    },
                options = KomgaSeriesStatus.entries.map {
                    LabeledEntry(
                        it,
                        stringResource(AppStrings.forPublicationStatus(it))
                    )
                },
                onOptionSelect = { changed -> filterState.onPublicationStatusSelect(changed.value) },
                label = stringResource(Res.string.series_filter_publication_status),
                modifier = filterItemModifier,
            )

            val authorsSelectedOptions = remember(currentFilter.authors) {
                currentFilter.authors.distinctBy { it.name }.map { LabeledEntry(it, it.name) }
            }
            val authorOptions = remember(filterState.authorsOptions) {
                filterState.authorsOptions.distinctBy { it.name }.map { LabeledEntry(it, it.name) }
            }
            FilterDropdownMultiChoiceWithSearch(
                selectedOptions = authorsSelectedOptions,
                options = authorOptions,
                onOptionSelect = { author -> filterState.onAuthorSelect(author.value) },
                onSearch = filterState::onAuthorsSearch,
                label = stringResource(Res.string.series_filter_authors),
                modifier = filterItemModifier,
            )

            FilterDropdownMultiChoice(
                selectedOptions = currentFilter.publishers.map { stringEntry(it) },
                options = filterState.publishersOptions.map { stringEntry(it) },
                onOptionSelect = { changed -> filterState.onPublisherSelect(changed.value) },
                label = stringResource(Res.string.series_filter_publisher),
                modifier = filterItemModifier,
            )

            FilterDropdownMultiChoice(
                selectedOptions = currentFilter.languages.map { stringEntry(it) },
                options = filterState.languagesOptions.map { stringEntry(it) },
                onOptionSelect = { changed -> filterState.onLanguageSelect(changed.value) },
                label = stringResource(Res.string.series_filter_language),
                modifier = filterItemModifier,
            )
            FilterDropdownMultiChoice(
                selectedOptions = currentFilter.releaseDates.map { stringEntry(it) },
                options = filterState.releaseDateOptions.map { stringEntry(it) },
                onOptionSelect = { changed -> filterState.onReleaseDateSelect(changed.value) },
                label = stringResource(Res.string.series_filter_release_date),
                modifier = filterItemModifier,
            )

            FilterDropdownMultiChoice(
                selectedOptions = currentFilter.ageRatings.map { stringEntry(it) },
                options = filterState.ageRatingsOptions.map { stringEntry(it) },
                onOptionSelect = { changed -> filterState.onAgeRatingSelect(changed.value) },
                label = stringResource(Res.string.series_filter_age_rating),
                modifier = filterItemModifier,
            )

            Row(
                modifier = filterItemModifier,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(controlHeight)
                        .clickable { filterState.onCompletionToggle() }
                        .cursorForHand()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clip(RoundedCornerShape(5.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val checkboxState by derivedStateOf {
                        when (currentFilter.complete) {
                            ANY -> ToggleableState.Off
                            COMPLETE -> ToggleableState.On
                            INCOMPLETE -> ToggleableState.Indeterminate
                        }

                    }
                    TriStateCheckbox(
                        state = checkboxState,
                        onClick = filterState::onCompletionToggle,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        stringResource(Res.string.series_filter_complete),
                        style = MaterialTheme.typography.labelLarge, maxLines = 2
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(controlHeight)
                        .clickable { filterState.onFormatToggle() }
                        .cursorForHand()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clip(RoundedCornerShape(5.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val checkboxState by derivedStateOf {
                        when (currentFilter.oneshot) {
                            Format.ANY -> ToggleableState.Off
                            Format.ONESHOT -> ToggleableState.On
                            Format.NOT_ONESHOT -> ToggleableState.Indeterminate
                        }

                    }
                    TriStateCheckbox(
                        state = checkboxState,
                        onClick = filterState::onFormatToggle,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        stringResource(Res.string.series_filter_oneshot),
                        style = MaterialTheme.typography.labelLarge, maxLines = 2
                    )
                }
            }
            }
        }

    }
}

@Composable
private fun SeriesFilterField(label: String, compact: Boolean, modifier: Modifier,
    content: @Composable (Modifier) -> Unit) {
    if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
        content(Modifier.weight(1f))
    } else content(modifier)
}
