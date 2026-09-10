package snd.komelia.offline.mediacontainer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ComicArchiveFormat(val mediaType: String) {
    ZIP("application/zip"), RAR("application/x-rar-compressed"), SEVEN_ZIP("application/x-7z-compressed"),
}

data class ComicArchiveEntry(val name: String, val size: Long?)

/** Import and page reading consume the same validated archive, irrespective of its suffix. */
interface OpenedComicArchive : AutoCloseable {
    val format: ComicArchiveFormat
    val entries: List<ComicArchiveEntry>
    fun readEntryBytes(name: String, checkCancelled: () -> Unit = {}): ByteArray
}

/** Transient work state, never persisted and containing no source names or account data. */
object ArchivePreparation {
    private val mutableActiveCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = mutableActiveCount.asStateFlow()

    internal fun started() = mutableActiveCount.update { it + 1 }
    internal fun finished() = mutableActiveCount.update { (it - 1).coerceAtLeast(0) }
}
