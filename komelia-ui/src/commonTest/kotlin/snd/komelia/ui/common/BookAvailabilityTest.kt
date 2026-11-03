package snd.komelia.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import snd.komelia.ui.home.homeReadingBook
import snd.komga.client.library.KomgaLibraryId

class BookAvailabilityTest {
    @Test fun aCachedOrLocalCopyRemainsReadableWhenTheRemoteSourceDisappears() {
        val cached = homeReadingBook("cached").copy(deleted = true, remoteFileUnavailable = true)
        assertTrue(bookContentIsAvailable(cached, libraryUnavailable = true))
        val local = cached.copy(downloaded = false, libraryId = KomgaLibraryId("local-library-synthetic"))
        assertTrue(bookContentIsAvailable(local, libraryUnavailable = true))
    }

    @Test fun remoteOnlyBooksRequireAnAvailableFileAndLibrary() {
        val remote = homeReadingBook("remote").copy(downloaded = false)
        assertTrue(bookContentIsAvailable(remote, libraryUnavailable = false))
        assertFalse(bookContentIsAvailable(remote, libraryUnavailable = true))
        assertFalse(bookContentIsAvailable(remote.copy(deleted = true), libraryUnavailable = false))
        assertFalse(bookContentIsAvailable(remote.copy(remoteFileUnavailable = true), libraryUnavailable = false))
    }
}
