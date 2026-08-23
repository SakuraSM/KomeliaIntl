package snd.komelia.offline.user.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineUserTest {
    @Test
    fun rootUserDoesNotDependOnAPersistedServerUserRecord() {
        val root = OfflineUser.ROOT_USER

        assertEquals(OfflineUser.ROOT, root.id)
        assertNull(root.serverId)
        assertTrue(root.sharedAllLibraries)
        assertTrue("ADMIN" in root.roles)
    }
}
