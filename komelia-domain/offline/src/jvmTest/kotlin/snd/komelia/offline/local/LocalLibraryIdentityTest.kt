package snd.komelia.offline.local

import snd.komga.client.library.KomgaLibraryId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalLibraryIdentityTest {
    @Test
    fun identifiesOnlyLocalLibraryIds() {
        assertTrue(KomgaLibraryId("local-library-device-books").isLocalLibrary())
        assertFalse(KomgaLibraryId("remote-library").isLocalLibrary())
        assertFalse(KomgaLibraryId("my-local-library-copy").isLocalLibrary())
    }
}
