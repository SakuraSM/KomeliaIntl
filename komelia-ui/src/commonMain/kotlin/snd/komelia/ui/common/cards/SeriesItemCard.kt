package snd.komelia.ui.common.cards

import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.actions_show_more
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_books_count
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_unavailable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_books_progress
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.series.mangabaka.state.MangaBakaTitlesState

@Composable
fun SeriesImageCard(
    series: KomeliaSeries,
    onSeriesClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onSeriesSelect: (() -> Unit)? = null,
    seriesMenuActions: SeriesMenuActions? = null,
    modifier: Modifier = Modifier,
    coverWidth: Dp? = null,
    coverAspectRatio: Float = 2f / 3f,
) {
    val libraries = LocalLibraries.current
    val libraryIsDeleted = remember {
        libraries.value.firstOrNull { it.id == series.libraryId }?.unavailable ?: false
    }
    ItemCard(
        modifier = modifier,
        coverWidth = coverWidth,
        coverAspectRatio = coverAspectRatio,
        onClick = onSeriesClick,
        onLongClick = onSeriesSelect,
        image = {
            Box {
                SeriesCardHoverOverlay(
                    series = series,
                    onSeriesSelect = onSeriesSelect,
                    isSelected = isSelected,
                    seriesActions = seriesMenuActions,
                ) {
                    SeriesImageOverlay(
                        series = series,
                        libraryIsDeleted = libraryIsDeleted,
                        showTitle = false,
                    ) {
                        SeriesThumbnail(
                            series.id,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                if (series.deleted || libraryIsDeleted) {
                    Box(Modifier.align(Alignment.BottomStart)) {
                        CardStatusBadge(stringResource(Res.string.series_unavailable))
                    }
                }
            }
        },
        content = {
            ReadingCoverCaption(
                title = series.metadata.title,
                progress = if (series.booksCount > 0) series.booksReadCount.toFloat() / series.booksCount else null,
                status = stringResource(Res.string.series_books_progress, series.booksReadCount, series.booksCount),
            )
        },
    )
}

@Composable
fun SeriesSimpleImageCard(
    series: KomeliaSeries,
    onSeriesClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ItemCard(
        modifier = modifier,
        onClick = onSeriesClick,
        image = {
            SeriesImageOverlay(
                series = series,
                libraryIsDeleted = false,
                showTitle = false,
            ) {
                SeriesThumbnail(
                    series.id,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    )
}

@Composable
private fun SeriesCardHoverOverlay(
    series: KomeliaSeries,
    isSelected: Boolean,
    onSeriesSelect: (() -> Unit)?,
    seriesActions: SeriesMenuActions?,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered = interactionSource.collectIsHoveredAsState()
    var isActionsMenuExpanded by remember { mutableStateOf(false) }
    val showOverlay = derivedStateOf { isHovered.value || isActionsMenuExpanded || isSelected }
    val border = if (showOverlay.value) overlayBorderModifier() else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .then(border),
        contentAlignment = Alignment.Center
    ) {
        content()

        if (showOverlay.value) {
            val backgroundModifier =
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = .5f))
                else Modifier
            Column(backgroundModifier.fillMaxSize()) {
                if (onSeriesSelect != null) {
                    SelectionRadioButton(isSelected, onSeriesSelect)
                    Spacer(Modifier.weight(1f))
                }

                if (seriesActions != null) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Spacer(Modifier.weight(1f))

                        Box {
                            IconButton(
                                onClick = { isActionsMenuExpanded = true },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(Res.string.actions_show_more))
                            }

                            SeriesActionsMenu(
                                series = series,
                                actions = seriesActions,
                                expanded = isActionsMenuExpanded,
                                showEditOption = true,
                                showDownloadOption = true,
                                onDismissRequest = { isActionsMenuExpanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesImageOverlay(
    series: KomeliaSeries,
    libraryIsDeleted: Boolean,
    showTitle: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
        if (series.libraryId.isLocalLibrary()) {
            LocalSourceBadge(Modifier.padding(6.dp))
        }
        if (showTitle) {
            CardGradientOverlay()
        }

        if (series.booksUnreadCount > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${series.booksUnreadCount}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (showTitle) {
                val mangaBaka = series.mangaBakaMetadata
                val title = if (mangaBaka != null) {
                    remember(series) { MangaBakaTitlesState(mangaBaka).mainTitle.title }
                } else {
                    series.metadata.title
                }
                CardOutlinedText(text = title, maxLines = 2)
                if (series.deleted || libraryIsDeleted) {
                    CardOutlinedText(
                        stringResource(Res.string.series_unavailable),
                        textColor = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesDetailedListCard(
    series: KomeliaSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: SeriesMenuActions? = null,
) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.heightIn(min = 112.dp).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SeriesThumbnail(series.id, modifier = Modifier.width(64.dp).aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.extraSmall), contentScale = ContentScale.Fit)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(series.metadata.title, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(Res.plurals.series_books_count, series.booksCount, series.booksCount.toString()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (menu != null) {
                var expanded by remember(series.id) { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.actions_show_more)) }
                    SeriesActionsMenu(series, menu, expanded, showEditOption = true, showDownloadOption = true,
                        onDismissRequest = { expanded = false })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
