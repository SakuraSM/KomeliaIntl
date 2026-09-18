package snd.komelia.ui

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MainScreenIdentityTest {
    @Test fun aNewLoginMustNotRestoreAnotherSessionsNavigationState() {
        val serverA = MainScreen()
        val serverB = MainScreen()
        assertNotEquals(serverA.key, serverB.key)
    }

    @Test fun recreationRetainsTheSameSessionIdentity() {
        val original = MainScreen()
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(original) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as MainScreen }
        assertEquals(original.key, restored.key)
    }

    @Test fun legacySerializedScreensRemainReadable() {
        assertEquals(8985746227260504224L, java.io.ObjectStreamClass.lookup(MainScreen::class.java).serialVersionUID)
        val legacy = MainScreen()
        MainScreen::class.java.getDeclaredField("navigationId").apply { isAccessible = true }.set(legacy, null)
        val restoredKey = legacy.key
        assertEquals(restoredKey, legacy.key)
        assertNotEquals(MainScreen().key, restoredKey)
    }
}
