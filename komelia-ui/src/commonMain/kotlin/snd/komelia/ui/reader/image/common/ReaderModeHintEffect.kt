package snd.komelia.ui.reader.image.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState

@Composable
internal fun ReaderModeHintEffect(
    ready: Boolean,
    requestId: String?,
    lastHint: MutableState<String?>,
    onHint: suspend () -> Unit,
) {
    LaunchedEffect(ready, requestId) {
        if (!ready) return@LaunchedEffect
        val token = requestId ?: "initial"
        if (lastHint.value == token || (requestId == null && lastHint.value != null)) return@LaunchedEffect
        onHint()
        lastHint.value = token
    }
}
