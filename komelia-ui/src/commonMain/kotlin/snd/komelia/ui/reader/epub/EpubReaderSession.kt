package snd.komelia.ui.reader.epub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** A fresh lease for each native WebView document, never reused on re-entry. */
internal class EpubReaderSession {
    private val isOpen = MutableStateFlow(true)

    val exitRequests = EpubExitSession()

    fun close() {
        isOpen.value = false
        exitRequests.retire()
    }

    fun requireOpen() {
        if (!isOpen.value) throw CancellationException("EPUB reader session closed")
    }

    suspend fun <Result> run(request: suspend () -> Result): Result {
        requireOpen()
        val result = request()
        requireOpen()
        return result
    }
}
