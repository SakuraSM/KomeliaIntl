package snd.komelia.http

import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import snd.komelia.settings.SecretsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiKeyStoreTest {
    @Test fun switchingServersNeverReusesAnotherServersKey() = runTest {
        val url = MutableStateFlow(Url("https://a.example"))
        val store = ApiKeyStore(url, MemorySecrets())
        store.setApiKey("https://a.example", "key-a")
        assertEquals("key-a", store.apiKey)
        url.value = Url("https://b.example")
        assertNull(store.apiKey)
        store.setApiKey("https://b.example", "key-b")
        assertEquals("key-b", store.apiKey)
        url.value = Url("https://a.example")
        assertEquals("key-a", store.apiKey)
        store.deleteApiKey("https://a.example")
        assertNull(store.apiKey)
    }

    @Test fun restoredKeyBelongsOnlyToItsServer() = runTest {
        val secrets = MemorySecrets()
        val url = MutableStateFlow(Url("https://a.example"))
        ApiKeyStore(url, secrets).setApiKey("https://a.example", "saved")
        val restored = ApiKeyStore(url, secrets)
        restored.loadStoredApiKey()
        assertEquals("saved", restored.apiKey)
        url.value = Url("https://b.example")
        restored.loadStoredApiKey()
        assertNull(restored.apiKey)
    }

    private class MemorySecrets : SecretsRepository {
        private val keys = mutableMapOf<String, String>()
        override suspend fun getCookie(url: String): String? = null
        override suspend fun setCookie(url: String, cookie: String) {}
        override suspend fun deleteCookie(url: String) {}
        override suspend fun getApiKey(url: String) = keys[url]
        override suspend fun setApiKey(url: String, apiKey: String) { keys[url] = apiKey }
        override suspend fun deleteApiKey(url: String) { keys.remove(url) }
    }
}
