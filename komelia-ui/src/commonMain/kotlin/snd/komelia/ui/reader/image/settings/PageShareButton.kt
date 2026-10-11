package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_share_page
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.reader.image.PageShareAction
import snd.komelia.ui.LocalKomeliaLayout

@Composable
internal fun PageShareButton(action: PageShareAction, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.reader_share_page)
    IconButton(
        onClick = action.onShare,
        enabled = !action.isSharing,
        modifier = modifier.size(LocalKomeliaLayout.current.minimumTouchTarget).semantics { contentDescription = label },
    ) {
        if (action.isSharing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else Icon(Icons.Default.Share, contentDescription = null)
    }
}
