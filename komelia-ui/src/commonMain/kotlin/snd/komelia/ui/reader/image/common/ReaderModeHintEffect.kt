package snd.komelia.ui.reader.image.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import snd.komelia.settings.model.ReaderType

internal fun readerModeHintSignature(type: ReaderType, direction: String): String = "${type.name}:$direction"

@Composable
internal fun ReaderModeHintEffect(
    ready: Boolean,
    requestId: String?,
    signature: String?,
    lastHint: MutableState<String?>,
    consumeHint: suspend (String) -> Boolean,
    onFailure: suspend (Throwable) -> Unit,
    onHint: suspend () -> Unit,
) {
    LaunchedEffect(ready, requestId, signature) {
        if (!ready || signature == null) return@LaunchedEffect
        val token = "${requestId ?: "initial"}:$signature"
        if (lastHint.value == token) return@LaunchedEffect
        try {
            if (consumeHint(signature)) {
                currentCoroutineContext().ensureActive()
                onHint()
            }
            lastHint.value = token
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            currentCoroutineContext().ensureActive()
            // Do not repeatedly report a failed write on recomposition in this session.
            lastHint.value = token
            onFailure(failure)
        }
    }
}
