package snd.komelia.ui.reader.image

import java.lang.reflect.Proxy
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.offline.api.OfflineReadListApi
import snd.komelia.ui.BookSiblingsContext
import snd.komga.client.book.KomgaBookId
import snd.komga.client.readlist.KomgaReadListId
import kotlin.test.*

class ReaderSiblingTest {
    @Test fun missingChapterIsAnEndButRequestFailureIsRetryable() = runBlocking {
        assertSame(SiblingLoad.End, loadSibling<String, String>({ null }, { it }))
        val failure = IOException("synthetic timeout")
        var fail = true
        suspend fun request() = loadSibling({ if (fail) throw failure else "chapter 1.5" }, { it })
        assertSame(failure, assertIs<SiblingLoad.Failed>(request()).cause)
        fail = false
        assertEquals("chapter 1.5", assertIs<SiblingLoad.Available<String>>(request()).value)
    }

    @Test fun pageMetadataFailureDoesNotHideTheCurrentChapterOrBecomeSeriesEnd() = runBlocking {
        val failure = IOException("synthetic page-list failure")
        val result = loadSibling({ "known next chapter" }, { throw failure })
        assertSame(failure, assertIs<SiblingLoad.Failed>(result).cause)
    }

    @Test fun cancellationPropagatesDuringBothQueryAndPagePreparation() = runBlocking {
        assertFailsWith<CancellationException> {
            loadSibling<String, String>({ throw CancellationException() }, { it })
        }
        assertFailsWith<CancellationException> {
            loadSibling({ "next" }, { throw CancellationException() })
        }
        Unit
    }

    @Test fun onlineReadListOrderDoesNotDependOnDownloadedContent() = runBlocking {
        val calls = mutableListOf<String>()
        val bookApi = stub<KomgaBookApi> { calls.add("series:$it"); null }
        val readListApi = stub<KomgaReadListApi> { calls.add("list:$it"); null }
        val context = BookSiblingsContext.ReadList(KomgaReadListId("custom-order"))
        queryReaderSibling(KomgaBookId("downloaded"), context, true, bookApi, readListApi)
        queryReaderSibling(KomgaBookId("downloaded"), context, false, bookApi, readListApi)
        assertEquals(listOf("list:getBookSiblingNext", "list:getBookSiblingPrevious"), calls)
    }

    @Test fun explicitOfflineReadListKeepsItsExistingSeriesFallback() = runBlocking {
        val calls = mutableListOf<String>()
        val bookApi = stub<KomgaBookApi> { calls.add(it); null }
        val context = BookSiblingsContext.ReadList(KomgaReadListId("offline-list"))
        queryReaderSibling(KomgaBookId("downloaded"), context, true, bookApi, OfflineReadListApi())
        queryReaderSibling(KomgaBookId("downloaded"), context, false, bookApi, OfflineReadListApi())
        assertEquals(listOf("getBookSiblingNext", "getBookSiblingPrevious"), calls)
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as T
}
