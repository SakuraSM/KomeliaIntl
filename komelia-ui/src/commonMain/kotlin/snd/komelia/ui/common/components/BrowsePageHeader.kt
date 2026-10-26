package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout

@Composable
internal fun BrowsePageHeader(title: String, onSearchClick: (() -> Unit)? = null) {
    val layout = LocalKomeliaLayout.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = layout.pageHorizontalPadding + 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        if (onSearchClick != null) {
            KomeliaChromeSurface(shape = RoundedCornerShape(50)) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Rounded.Search, stringResource(Res.string.navbar_search))
                }
            }
        }
    }
}
