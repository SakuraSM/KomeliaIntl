package snd.komelia.ui.common.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.platform.WindowSizeClass

/** Measure the text first; the cover fills that height without contributing image intrinsic size. */
@Composable
internal fun DetailedListCardLayout(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val compact = LocalWindowWidth.current == WindowSizeClass.COMPACT ||
        LocalWindowWidth.current == WindowSizeClass.MEDIUM
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(layout.cardContentPadding)) {
        Box(
            Modifier.width(if (compact) 104.dp else 130.dp)
                .heightIn(min = if (compact) 148.dp else 185.dp)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Box(Modifier.matchParentSize(), content = cover)
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = layout.itemSpacing)) {
            content()
        }
    }
}
