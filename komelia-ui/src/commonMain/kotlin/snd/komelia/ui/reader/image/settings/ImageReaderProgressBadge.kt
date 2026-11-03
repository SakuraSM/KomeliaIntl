package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.components.KomeliaChromeSurface

/** A read-only location display. The reader retains ownership of page selection. */
@Composable
internal fun ImageReaderProgressBadge(pageLabel: String?, pageCount: Int, modifier: Modifier = Modifier) {
    if (pageLabel.isNullOrBlank() || pageCount <= 0) return
    KomeliaChromeSurface(modifier.height(26.dp).widthIn(min=66.dp).testTag("image-reader-page-badge"),
        shape=RoundedCornerShape(13.dp)) {
        Box(Modifier.widthIn(min=66.dp).height(26.dp).padding(horizontal=10.dp),contentAlignment=Alignment.Center) {
            Text("$pageLabel / $pageCount",style=MaterialTheme.typography.bodyMedium,maxLines=1)
        }
    }
}
