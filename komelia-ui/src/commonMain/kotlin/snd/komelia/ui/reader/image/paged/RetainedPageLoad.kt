package snd.komelia.ui.reader.image.paged

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** A cached load belongs to the reader window, not to the coroutine awaiting a page turn. */
internal class RetainedPageLoad<T>(
    scope: CoroutineScope,
    private val dispose: (T) -> Unit,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    load: suspend () -> T,
) {
    private sealed interface State<out T> {
        data object Loading : State<Nothing>
        data object Closed : State<Nothing>
        class Ready<T>(val value: T) : State<T>
    }

    private val state = MutableStateFlow<State<T>>(State.Loading)
    val deferred = scope.async(start = start) {
        val value = load()
        // Eviction can race a loader that has already created an image but not returned yet.
        if (!state.compareAndSet(State.Loading, State.Ready(value))) dispose(value)
        value
    }.also { job ->
        job.invokeOnCompletion { error -> if (error != null) release() }
    }

    fun close() {
        release()
        deferred.cancel()
    }

    fun isReusable(accept: (T) -> Boolean): Boolean = when (val current = state.value) {
        State.Loading -> true
        State.Closed -> false
        is State.Ready -> accept(current.value)
    }

    private fun release() {
        while (true) {
            val previous = state.value
            if (previous == State.Closed) return
            if (state.compareAndSet(previous, State.Closed)) {
                if (previous is State.Ready) dispose(previous.value)
                return
            }
        }
    }
}

/** Explicit window ownership prevents a late page load from repopulating a stopped reader. */
internal class RetainedPageCache<K, V>(
    private val scope: CoroutineScope,
    private val dispose: (V) -> Unit,
) {
    private data class Window<K, V>(
        val keys: Set<K> = emptySet(),
        val loads: Map<K, RetainedPageLoad<V>> = emptyMap(),
        val closed: Boolean = false,
    )

    private val state = MutableStateFlow(Window<K, V>())

    fun retain(keys: Set<K>) {
        require(keys.size <= 10)
        while (true) {
            val previous = state.value
            if (previous.closed) throw CancellationException("Reader stopped")
            val retained = previous.loads.filterKeys { it in keys }
            if (state.compareAndSet(previous, Window(keys, retained))) {
                previous.loads.filterKeys { it !in keys }.values.forEach { it.close() }
                return
            }
        }
    }

    fun getOrLoad(key: K, acceptCached: (V) -> Boolean = { true }, load: suspend () -> V): Deferred<V> {
        while (true) {
            val previous = state.value
            if (previous.closed || key !in previous.keys) throw CancellationException("Page outside reader window")
            previous.loads[key]?.takeIf { !it.deferred.isCancelled && it.isReusable(acceptCached) }
                ?.let { return it.deferred }
            val candidate = RetainedPageLoad(scope, dispose, CoroutineStart.LAZY, load)
            if (state.compareAndSet(previous, previous.copy(loads = previous.loads + (key to candidate)))) {
                previous.loads[key]?.close()
                candidate.deferred.start()
                return candidate.deferred
            }
            candidate.close()
        }
    }

    fun invalidate(key: K) {
        while (true) {
            val previous = state.value
            val removed = previous.loads[key] ?: return
            if (state.compareAndSet(previous, previous.copy(loads = previous.loads - key))) {
                removed.close()
                return
            }
        }
    }

    fun close() {
        while (true) {
            val previous = state.value
            if (previous.closed) return
            if (state.compareAndSet(previous, Window(closed = true))) {
                previous.loads.values.forEach { it.close() }
                return
            }
        }
    }
}

/** At most five spreads / ten pages, including the visible spread. */
internal fun spreadPreloadRange(index: Int, spreadCount: Int): IntRange =
    (index - 2).coerceAtLeast(0)..(index + 2).coerceAtMost(spreadCount - 1)
