package snd.komelia.ui.reader.epub

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

internal class TtuExitSession {
    private enum class State { NOT_READY, WAITING, REQUESTED, CLOSED }

    private val state = MutableStateFlow(State.NOT_READY)
    private val requests = Channel<Boolean>(Channel.CONFLATED)

    suspend fun awaitRequest(): Boolean {
        while (true) {
            val current = state.value
            if (current == State.CLOSED) return false
            if (state.compareAndSet(current, State.WAITING)) break
        }
        return requests.receiveCatching().getOrNull() ?: false
    }

    fun requestExit(): Boolean {
        while (true) {
            when (state.value) {
                State.NOT_READY, State.CLOSED -> return false
                State.REQUESTED -> return true
                State.WAITING -> if (state.compareAndSet(State.WAITING, State.REQUESTED)) {
                    requests.trySend(true)
                    return true
                }
            }
        }
    }

    fun retire(): Boolean {
        while (true) {
            val current = state.value
            if (current == State.CLOSED) return false
            if (state.compareAndSet(current, State.CLOSED)) break
        }
        requests.trySend(false)
        requests.close()
        return true
    }
}
