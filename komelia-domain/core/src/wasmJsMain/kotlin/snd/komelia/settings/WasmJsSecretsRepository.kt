package snd.komelia.settings

import kotlinx.browser.localStorage

private fun apiKeyKey(url: String) = "KomeliaApiKey:${io.ktor.http.Url(url)}"

class WasmJsSecretsRepository : SecretsRepository {
    override suspend fun getCookie(url: String): String? {
        return null
    }

    override suspend fun setCookie(url: String, cookie: String) {
    }

    override suspend fun deleteCookie(url: String) {
    }

    override suspend fun getApiKey(url: String): String? {
        return localStorage.getItem(apiKeyKey(url))
    }

    override suspend fun setApiKey(url: String, apiKey: String) {
        return localStorage.setItem(apiKeyKey(url), apiKey)
    }

    override suspend fun deleteApiKey(url: String) {
        return localStorage.removeItem(apiKeyKey(url))
    }
}
