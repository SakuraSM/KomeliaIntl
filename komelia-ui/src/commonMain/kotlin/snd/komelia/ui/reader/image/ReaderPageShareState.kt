package snd.komelia.ui.reader.image

import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_share_page_failed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import snd.komelia.AppNotifications
import snd.komelia.image.PageShareService
import snd.komelia.image.SharedPageImage
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId

data class PageShareAction(val isSharing: Boolean, val onShare: () -> Unit)

class ReaderPageShareState(
    private val bookApi: KomgaBookApi,
    private val service: PageShareService,
    private val notifications: AppNotifications,
    private val scope: CoroutineScope,
) {
    val isSharing = MutableStateFlow(false)

    fun share(bookId: KomgaBookId, pageNumber: Int) {
        if (pageNumber < 1 || !isSharing.compareAndSet(false, true)) return
        scope.launch {
            notifications.runCatchingToNotifications {
                try {
                    val bytes = bookApi.getPage(bookId, pageNumber)
                    service.share(SharedPageImage(bytes, pageNumber))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    throw IllegalStateException(getString(Res.string.reader_share_page_failed), error)
                }
            }
        }.invokeOnCompletion { isSharing.value = false }
    }
}
