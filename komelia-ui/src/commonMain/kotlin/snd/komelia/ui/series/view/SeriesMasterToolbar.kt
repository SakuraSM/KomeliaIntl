package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.common.components.FloatingChromeIconButton
import snd.komelia.ui.common.menus.SeriesActionsMenu
import snd.komelia.ui.common.menus.SeriesMenuActions

/** The list pane keeps its title and scope together; sort/filter remain in its menu. */
@Composable
internal fun SeriesMasterToolbar(
    series: KomeliaSeries?,
    actions: SeriesMenuActions,
    onBack: () -> Unit,
    onViewOptions: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 102.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 36.dp)) {
            FloatingChromeIconButton(onBack, floating = false) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back)) }
        }
        Column(Modifier.weight(1f).padding(top = 44.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(series?.metadata?.title ?: stringResource(Res.string.reading_series_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (series != null) {
                val authors = series.booksMetadata.authors.map { it.name }.distinct().joinToString("、")
                val count = pluralStringResource(Res.plurals.series_books_count, series.booksCount, series.booksCount.toString())
                Text(listOf(count, authors).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.padding(top = 36.dp)) {
            FloatingChromeIconButton({ expanded = true }, floating = false) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.actions_show_more)) }
            if (series != null) SeriesActionsMenu(series, actions, expanded, showEditOption = true, showDownloadOption = true,
                onDismissRequest = { expanded = false }, extraContent = {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.browse_options)) }, onClick = {
                        expanded = false
                        onViewOptions()
                    })
                })
        }
    }
}
