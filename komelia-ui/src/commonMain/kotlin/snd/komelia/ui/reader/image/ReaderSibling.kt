package snd.komelia.ui.reader.image

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.api.OfflineReadListApi
import snd.komelia.ui.BookSiblingsContext
import snd.komga.client.book.KomgaBookId

sealed interface SiblingLoad<out T> {
    data class Available<T>(val value: T) : SiblingLoad<T>
    data object End : SiblingLoad<Nothing>
    data class Failed(val cause: Throwable) : SiblingLoad<Nothing>
}

data class ReaderSibling(val book: KomeliaBook, val pages: List<PageMetadata>)

internal suspend fun <B, T> loadSibling(
    query: suspend () -> B?,
    prepare: suspend (B) -> T,
): SiblingLoad<T> {
    val book = try {
        query() ?: return SiblingLoad.End
    } catch (e: CancellationException) {
        throw e
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.NotFound) return SiblingLoad.End
        return SiblingLoad.Failed(e)
    } catch (e: Exception) {
        return SiblingLoad.Failed(e)
    }
    return try {
        SiblingLoad.Available(prepare(book))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A known chapter whose pages fail to load is not the end of the series.
        SiblingLoad.Failed(e)
    }
}

internal suspend fun queryReaderSibling(
    bookId: KomgaBookId,
    context: BookSiblingsContext,
    next: Boolean,
    bookApi: KomgaBookApi,
    readListApi: KomgaReadListApi,
): KomeliaBook? = when {
    // Preserve the existing offline read-list fallback. Online lists always keep list order.
    context is BookSiblingsContext.ReadList && readListApi !is OfflineReadListApi ->
        if (next) readListApi.getBookSiblingNext(context.id, bookId)
        else readListApi.getBookSiblingPrevious(context.id, bookId)
    next -> bookApi.getBookSiblingNext(bookId)
    else -> bookApi.getBookSiblingPrevious(bookId)
}
