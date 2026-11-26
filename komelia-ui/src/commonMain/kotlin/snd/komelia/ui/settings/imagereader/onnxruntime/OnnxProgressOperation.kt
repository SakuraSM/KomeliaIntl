package snd.komelia.ui.settings.imagereader.onnxruntime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import snd.komelia.updates.UpdateProgress

internal class OnnxProgressOperation {
    sealed interface Status {
        data object Idle : Status
        data class Running(val progress: UpdateProgress = UpdateProgress(0, 0)) : Status
        data object Succeeded : Status
        data class Failed(val error: Exception) : Status
    }

    var status: Status by mutableStateOf(Status.Idle)
        private set

    val isRunning: Boolean get() = status is Status.Running

    private var job: Job? = null

    fun start(scope: CoroutineScope, request: () -> Flow<UpdateProgress>) {
        if (isRunning || status == Status.Succeeded) return
        status = Status.Running()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                coroutineContext.ensureActive()
                request().conflate().collect { status = Status.Running(it) }
                status = Status.Succeeded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = Status.Failed(error)
            } finally {
                if (isRunning) status = Status.Idle
            }
        }
    }

    fun cancel() {
        if (isRunning) job?.cancel()
    }
}
