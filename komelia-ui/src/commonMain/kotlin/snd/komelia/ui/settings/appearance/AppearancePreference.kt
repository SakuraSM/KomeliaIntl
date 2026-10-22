package snd.komelia.ui.settings.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Coalesce rapid choices and finish an accepted write when the settings screen closes. */
internal class AppearancePreference<Value : Any>(
    initialValue: Value,
    private val scope: () -> CoroutineScope,
    private val save: suspend (Value) -> Unit,
) {
    var value by mutableStateOf(initialValue)
        private set
    var hasSaveError by mutableStateOf(false)
        private set
    private var savedValue = initialValue
    private var failedValue: Value? = null
    private val writeMutex = Mutex()

    fun restore(value: Value) {
        this.value = value
        savedValue = value
    }

    fun update(value: Value) {
        this.value = value
        hasSaveError = false
        failedValue = null
        scope().launch {
            writeMutex.withLock {
                withContext(NonCancellable) { saveLatestChoice() }
            }
        }
    }

    fun retry() {
        failedValue?.let(::update)
    }

    private suspend fun saveLatestChoice() {
        while (value != savedValue) {
            val requested = value
            try {
                save(requested)
                savedValue = requested
                hasSaveError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (value == requested) {
                    value = savedValue
                    failedValue = requested
                    hasSaveError = true
                    return
                }
                // An obsolete failure must not discard a newer choice queued during that write.
            }
        }
    }
}
