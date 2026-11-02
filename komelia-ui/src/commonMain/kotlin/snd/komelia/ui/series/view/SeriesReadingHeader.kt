package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_sibling_retry
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LoadState
import snd.komelia.ui.book.readingActionLabel
import snd.komelia.ui.common.readIsSupported

@Composable
internal fun SeriesReadingHeader(
    readingTarget: LoadState<KomeliaBook?>,
    onRead: (KomeliaBook) -> Unit,
    onRetry: () -> Unit,
    header: @Composable (readingAction: @Composable () -> Unit) -> Unit,
) {
    header {
        val target = (readingTarget as? LoadState.Success)?.value
        if (target != null && readIsSupported(target)) {
            Surface(onClick = { onRead(target) }, color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)) {
                        Box(Modifier.padding(horizontal = 8.dp).heightIn(min = 36.dp), contentAlignment = Alignment.Center) {
                            Text(readingActionLabel(target), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        } else if (readingTarget is LoadState.Error) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.reader_sibling_retry))
            }
        }
    }
}
