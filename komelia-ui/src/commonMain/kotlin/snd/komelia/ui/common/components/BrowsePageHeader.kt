package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout

@Composable
internal fun BrowsePageHeader(
    title: String,
    onSearchClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) = KomeliaSafeTopBar {
    val layout = LocalKomeliaLayout.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = layout.minimumTouchTarget).padding(horizontal = layout.pageHorizontalPadding + 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        navigationIcon?.invoke()
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
        if (onSearchClick != null) {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Rounded.Search, stringResource(Res.string.navbar_search))
            }
        }
    }
}
