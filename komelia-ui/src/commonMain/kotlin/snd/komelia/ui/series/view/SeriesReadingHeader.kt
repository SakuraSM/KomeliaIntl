package snd.komelia.ui.series.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    header: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        header()
        val target = (readingTarget as? LoadState.Success)?.value
        if (target != null && readIsSupported(target)) {
            Button(onClick = { onRead(target) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
                Spacer(Modifier.width(8.dp))
                Text(readingActionLabel(target))
            }
        } else if (readingTarget is LoadState.Error) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.reader_sibling_retry))
            }
        }
    }
}
