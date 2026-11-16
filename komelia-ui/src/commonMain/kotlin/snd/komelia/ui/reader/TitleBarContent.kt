package snd.komelia.ui.reader

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.platform.TitleBarScope

@Composable
internal fun TitleBarScope.MacReaderTitleBar(title: String? = null, onExit: (() -> Unit)? = null) {
    Text("Komelia", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
        modifier = Modifier.align(Alignment.Start).padding(start = 12.dp).nonInteractive())
    if (!title.isNullOrBlank()) Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 600.dp).nonInteractive())
    if (onExit != null) IconButton(onClick = onExit, modifier = Modifier.align(Alignment.End)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
    }
}

@Composable
fun TitleBarScope.TitleBarContent(
    title: String,
    onExit: () -> Unit,
) {
    IconButton(
        onClick = onExit,
        modifier = Modifier
            .align(Alignment.Start)
            .height(32.dp)
            .widthIn(min = 32.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            "Leave",
        )
    }
    Spacer(
        Modifier.width(10.dp)
            .align(Alignment.Start)
            .nonInteractive()
    )

    Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.heightIn(max = 32.dp)
            .align(Alignment.Start)
            .nonInteractive()
            .fillMaxWidth(.7f)
    )

}
