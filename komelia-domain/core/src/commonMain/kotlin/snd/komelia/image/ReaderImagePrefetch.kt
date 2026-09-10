package snd.komelia.image

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.MutableStateFlow

data class ImageViewport(
    val maxDisplaySize: IntSize,
    val zoomFactor: Float,
    val visibleDisplaySize: IntRect,
)

data class ReaderImagePrefetch(val viewports: List<ImageViewport>, val budget: ReaderPrefetchBudget)

/** Counts extra rendered pixels across every image in one reader, not just each page. */
class ReaderPrefetchBudget(val maximumBytes: Long = 32L * 1024 * 1024) {
    private val allocated = MutableStateFlow(0L)
    val usedBytes: Long get() = allocated.value

    init { require(maximumBytes >= 0) }

    fun reserve(bytes: Long): Reservation? {
        require(bytes >= 0)
        while (true) {
            val previous = allocated.value
            if (bytes > maximumBytes - previous) return null
            if (allocated.compareAndSet(previous, previous + bytes)) return Reservation(bytes)
        }
    }

    inner class Reservation internal constructor(private val bytes: Long) {
        private val released = MutableStateFlow(false)
        fun release() {
            if (!released.compareAndSet(false, true)) return
            while (true) {
                val previous = allocated.value
                if (allocated.compareAndSet(previous, previous - bytes)) return
            }
        }
    }
}
