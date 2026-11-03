package snd.komelia.ui.book

import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LoadState
import snd.komelia.ui.home.homeReadingBook
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class BookAdjacentBooksTest {
    @Test fun anAbsentPreviousBookDoesNotMoveTheNextBookIntoThePreviousSlot() = runTest {
        val next = homeReadingBook("next")
        val state = BookReadingNavigationState(api(null, next), homeReadingBook("current"), this)
        state.load()
        advanceUntilIdle()
        assertIs<LoadState.Success<Unit>>(state.state.value)
        assertNull(state.adjacentBooks.value.previous)
        assertSame(next, state.adjacentBooks.value.next)
    }

    @Test fun anAbsentNextBookKeepsThePreviousBookOnTheLeft() = runTest {
        val previous = homeReadingBook("previous")
        val state = BookReadingNavigationState(api(previous, null), homeReadingBook("current"), this)
        state.load()
        advanceUntilIdle()
        assertSame(previous, state.adjacentBooks.value.previous)
        assertNull(state.adjacentBooks.value.next)
    }

    @Test fun aFailedLookupRemainsRetryableInsteadOfBecomingAnEmptyBoundary() = runTest {
        val state = BookReadingNavigationState(api(null, null, failNext = true), homeReadingBook("current"), this)
        state.load()
        advanceUntilIdle()
        assertIs<LoadState.Error>(state.state.value)
        assertEquals(BookAdjacentBooks(), state.adjacentBooks.value)
    }

    private fun api(previous: KomeliaBook?, next: KomeliaBook?, failNext: Boolean = false): KomgaBookApi =
        Proxy.newProxyInstance(KomgaBookApi::class.java.classLoader, arrayOf(KomgaBookApi::class.java)) { _, method, _ ->
            when (method.name.substringBefore('-')) {
                "getBookSiblingPrevious" -> previous
                "getBookSiblingNext" -> if (failNext) throw IOException("Synthetic lookup failure") else next
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KomgaBookApi
}
