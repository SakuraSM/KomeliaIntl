package snd.komelia.http

import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import snd.komelia.settings.SecretsRepository
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RememberMePersistingCookieStoreTest {
    @Test fun invalidCurrentServerDoesNotPersistAnAuthenticationCookie() = runTest {
        val server = Url("https://a.example")
        val active = MutableStateFlow<Url?>(null)
        val secrets = RecordingSecrets()
        val store = RememberMePersistingCookieStore(active, MutableStateFlow(listOf(server)), secrets)
        val cookie = Cookie("komga-remember-me", "fixture", path = "/")
        store.addCookie(server, cookie)
        assertNull(secrets.cookie)
        active.value = server
        store.addCookie(server, cookie)
        assertNotNull(secrets.cookie)
        store.close()
    }

    private class RecordingSecrets : SecretsRepository {
        var cookie: String? = null
        override suspend fun getCookie(url: String) = cookie
        override suspend fun setCookie(url: String, cookie: String) { this.cookie = cookie }
        override suspend fun deleteCookie(url: String) { cookie = null }
        override suspend fun getApiKey(url: String): String? = null
        override suspend fun setApiKey(url: String, apiKey: String) {}
        override suspend fun deleteApiKey(url: String) {}
    }
}
