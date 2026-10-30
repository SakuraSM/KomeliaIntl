package snd.komelia.ui.series

import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.common.bookContentIsAvailable
import snd.komga.client.book.KomgaReadStatus
import snd.komga.client.common.KomgaSort
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.search.allOfBooks
import snd.komga.client.series.KomgaSeriesId

/** Resolve across the series, independently of the visible page and its filters. */
internal suspend fun findSeriesReadingTarget(bookApi: KomgaBookApi, seriesId: KomgaSeriesId): KomeliaBook? {
    for (status in listOf(KomgaReadStatus.IN_PROGRESS, KomgaReadStatus.UNREAD, null)) {
        var pageIndex = 0
        do {
            val result = bookApi.getBookList(
                conditionBuilder = allOfBooks {
                    this.seriesId { isEqualTo(seriesId) }
                    if (status != null) readStatus { isEqualTo(status) }
                },
                pageRequest = KomgaPageRequest(pageIndex = pageIndex, size = 20,
                    sort = if (status == KomgaReadStatus.IN_PROGRESS) KomgaSort.KomgaBooksSort.byReadDate(KomgaSort.Direction.DESC) else KomgaSort.KomgaBooksSort.byNumberAsc()),
            )
            result.content.firstOrNull { bookContentIsAvailable(it, false) }?.let { return it }
            pageIndex++
        } while (result.content.isNotEmpty() && pageIndex < result.totalPages)
    }
    return null
}
