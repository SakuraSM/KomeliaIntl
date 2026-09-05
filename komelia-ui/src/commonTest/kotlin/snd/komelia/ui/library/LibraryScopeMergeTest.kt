package snd.komelia.ui.library

import snd.komga.client.library.KomgaLibraryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryScopeMergeTest {
    @Test
    fun `local scope replaces duplicate remote scope and every id remains unique`() {
        val remote = scope("remote-library", "Remote")
        val duplicatedRemoteLocal = scope("local-library-books", "Cached local")
        val local = scope("local-library-books", "Device books", isLocal = true)

        val merged = mergeLibraryScopes(
            remote = listOf(remote, duplicatedRemoteLocal, duplicatedRemoteLocal),
            local = listOf(local, local),
        )

        assertEquals(listOf(remote.id, local.id), merged.map { it.id })
        assertEquals(merged.size, merged.map { it.id }.distinct().size)
        assertTrue(merged.last().isLocal)
        assertEquals("Device books", merged.last().name)
    }

    private fun scope(
        id: String,
        name: String,
        isLocal: Boolean = false,
    ) = LibraryScopeItem(
        id = KomgaLibraryId(id),
        name = name,
        unavailable = false,
        isLocal = isLocal,
    )
}
