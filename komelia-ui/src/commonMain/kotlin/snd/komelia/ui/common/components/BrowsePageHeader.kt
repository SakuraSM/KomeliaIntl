package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.ReadingDesign

@Composable
internal fun BrowsePageHeader(
    title: String,
    onSearchClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    titleContent: (@Composable (Modifier) -> Unit)? = null,
    searchButtonModifier: Modifier = Modifier,
    prominent: Boolean = false,
    titleStyle: TextStyle? = null,
    horizontalInset: Dp? = null,
    headerHeight: Dp? = null,
    titleOffset: Dp? = null,
    actions: @Composable RowScope.() -> Unit = {},
) = KomeliaSafeTopBar {
    val layout = LocalKomeliaLayout.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = headerHeight ?: if (prominent) ReadingDesign.primaryHeaderHeight else 56.dp)
            .padding(horizontal = horizontalInset ?: if (prominent) ReadingDesign.pageInset else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        navigationIcon?.invoke()
        if (titleContent == null) {
            Text(title, style = titleStyle ?: if (prominent) MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp) else MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (!prominent && navigationIcon == null) 16.dp else 0.dp)
                    .offset(y = titleOffset ?: if (prominent && titleStyle == null) 3.dp else 0.dp))
        } else {
            titleContent(Modifier.weight(1f))
        }
        actions()
        if (onSearchClick != null) {
            IconButton(onClick = onSearchClick, modifier = searchButtonModifier) {
                Icon(Icons.Rounded.Search, stringResource(Res.string.navbar_search))
            }
        }
    }
}
