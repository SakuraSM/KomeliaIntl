package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import kotlin.math.roundToInt

/** Progress belongs below the artwork, with equal caption space for unread and long titles. */
@Composable
internal fun ReadingCoverCaption(title: String, progress: Float?, status: String, supportingText: String? = null, prominent: Boolean = false, compact: Boolean = false) {
    if (compact) {
        Column(Modifier.fillMaxWidth().height(42.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)).padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (progress != null && progress > 0f) LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.weight(1f).height(3.dp), gapSize = 0.dp, drawStopIndicator = {},
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant)
                else Spacer(Modifier.weight(1f))
                Text(if (progress != null && progress > 0f) "${(progress * 100).roundToInt()}%" else status,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().height((if (prominent) 116.dp else if (supportingText == null) 66.dp else 84.dp) * LocalDensity.current.fontScale.coerceAtLeast(1f))
        .padding(top = if (prominent) 16.dp else 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(if (prominent) 3.dp else 4.dp)) {
        Text(title, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val statusStyle = if (prominent) MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 24.sp) else MaterialTheme.typography.labelSmall
        if (supportingText != null) Text(supportingText, style = if (prominent) statusStyle else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        if (progress != null && progress > 0f) LinearProgressIndicator(progress = { progress },
            modifier = Modifier.fillMaxWidth().height(if (prominent) 6.dp else 3.dp), color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
        Text(status, style = statusStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
