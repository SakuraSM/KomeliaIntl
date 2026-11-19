package snd.komelia.ui.login

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LoginScreenSessionTest {
    @Test
    fun separateLoginScreensHaveSeparateStableNavigationKeys() {
        val first = LoginScreen()
        val second = LoginScreen()
        assertNotEquals(first.key, second.key)
        assertEquals(first.key, roundTrip(first).key)
    }

    @Test
    fun previouslySavedLoginScreenStillRestores() {
        val fixture = checkNotNull(javaClass.getResourceAsStream("/synthetic-login-screen-before-session-id.ser"))
        val restored = ObjectInputStream(fixture).use { it.readObject() as LoginScreen }
        assertTrue(restored.key.isNotBlank())
        assertEquals(restored.key, roundTrip(restored).key)
    }

    private fun roundTrip(screen: LoginScreen): LoginScreen {
        val bytes = ByteArrayOutputStream().also { ObjectOutputStream(it).use { output -> output.writeObject(screen) } }
        return ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as LoginScreen }
    }
}
