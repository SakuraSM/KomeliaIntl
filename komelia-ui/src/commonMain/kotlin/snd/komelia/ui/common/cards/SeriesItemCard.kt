package snd.komelia.ui.common.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_unavailable
import org.jetbrains.compose.resources.stringResource
import snd.komelia.offline.local.isLocalLibrary
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.images.SeriesThumbnail
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions
import snd.komelia.ui.platform.cursorForHand
import snd.komga.client.series.KomgaSeries

@Composable
fun SeriesImageCard(
    series: KomgaSeries,
    onSeriesClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    onSeriesSelect: (() -> Unit)? = null,
    seriesMenuActions: SeriesMenuActions? = null,
    modifier: Modifier = Modifier,
) {
    val libraries = LocalLibraries.current
    val libraryIsDeleted = remember {
        libraries.value.firstOrNull { it.id == series.libraryId }?.unavailable ?: false
    }
    ItemCard(
        modifier = modifier,
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
                            contentScale = ContentScale.Crop
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
            CoverCardCaption(
                title = series.metadata.title,
                variant = CoverCaptionVariant.TitleOnly,
            )
        },
    )
}

@Composable
fun SeriesSimpleImageCard(
    series: KomgaSeries,
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
                    contentScale = ContentScale.Crop
                )
            }
        }
    )
}

@Composable
private fun SeriesCardHoverOverlay(
    series: KomgaSeries,
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
                                Icon(Icons.Rounded.MoreVert, contentDescription = null)
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
    series: KomgaSeries,
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

                CardOutlinedText(text = series.metadata.title, maxLines = 2)
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
    series: KomgaSeries,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier
            .cursorForHand()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        DetailedListCardLayout(
            cover = {
                SeriesImageOverlay(series = series, libraryIsDeleted = false, showTitle = false) {
                    SeriesThumbnail(series.id, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            },
            content = { SeriesDetails(series) },
        )
    }
}

@Composable
private fun SeriesDetails(series: KomgaSeries) {
    val layout = LocalKomeliaLayout.current
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                series.metadata.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (series.metadata.genres.isNotEmpty()) MetadataTagFlow(
            values = series.metadata.genres,
            width = LocalWindowWidth.current,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (series.metadata.summary.isNotBlank()) Text(
            series.metadata.summary, maxLines = 3, style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis,
        )

    }
}
