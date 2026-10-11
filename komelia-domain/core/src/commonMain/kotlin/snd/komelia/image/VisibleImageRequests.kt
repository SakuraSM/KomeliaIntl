package snd.komelia.image

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One atomic state is both the latest viewport and the renderer's conflated queue. */
internal class VisibleImageRequests<T> {
    data class Versioned<T>(val request: T, val serial: Long)

    private val mutable = MutableStateFlow<Versioned<T>?>(null)
    val state = mutable.asStateFlow()
    val value get() = state.value
    val serial get() = value?.serial ?: 0L

    fun submit(request: T, force: Boolean = false) {
        mutable.update { previous ->
            if (!force && previous?.request == request) previous
            else Versioned(request, (previous?.serial ?: 0L) + 1)
        }
    }

    fun reload(fallback: T?) {
        mutable.update { previous ->
            val request = previous?.request ?: fallback ?: return@update previous
            Versioned(request, (previous?.serial ?: 0L) + 1)
        }
    }
}
