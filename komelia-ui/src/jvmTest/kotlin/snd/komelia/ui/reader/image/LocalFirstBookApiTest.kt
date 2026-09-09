package snd.komelia.ui.reader.image

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Test
import snd.komelia.api.LocalFirstBookApi
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komga.client.book.KomgaBookId
import kotlin.test.assertEquals

class LocalFirstBookApiTest {
    @Test fun downloadedRemoteBooksStillUseTheCompleteOnlineChapterDirectory() = runBlocking {
        val calls = mutableListOf<String>()
        val remote = stub<KomgaBookApi> { name -> calls.add("remote:$name"); null }
        val local = stub<KomgaBookApi> { name -> calls.add("local:$name"); null }
        val repository = stub<OfflineBookRepository> { true }
        val api = LocalFirstBookApi(remote, local, repository)

        api.getBookSiblingNext(KomgaBookId("remote-downloaded-chapter-1"))
        api.getBookSiblingPrevious(KomgaBookId("remote-downloaded-chapter-5"))

        assertEquals(listOf("remote:getBookSiblingNext", "remote:getBookSiblingPrevious"), calls)
    }

    @Test fun localSourceBooksNeverRequestRemoteChapters() = runBlocking {
        val calls = mutableListOf<String>()
        val api = LocalFirstBookApi(
            stub { name -> calls.add("remote:$name"); null },
            stub { name -> calls.add("local:$name"); null },
            stub { true },
        )
        api.getBookSiblingNext(KomgaBookId("local-book-chapter-1"))
        api.getBookSiblingPrevious(KomgaBookId("local-book-chapter-5"))
        assertEquals(listOf("local:getBookSiblingNext", "local:getBookSiblingPrevious"), calls)
    }

    private inline fun <reified T> stub(crossinline answer: (String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name.substringBefore('-'))
        } as T
}
