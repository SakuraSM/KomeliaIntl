package snd.komelia.http

import io.ktor.http.*
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.settings.SecretsRepository

class ApiKeyStore(
    private val komgaUrl: StateFlow<Url>,
    private val secretsRepository: SecretsRepository,
) {
    private val keys = mutableMapOf<String, String>()
    val apiKey: String?
        get() = keys[komgaUrl.value.toString()]

    suspend fun loadStoredApiKey() {
        val url = komgaUrl.value
        val key = secretsRepository.getApiKey(url.toString())
        if (key == null) keys.remove(url.toString()) else keys[url.toString()] = key
    }

    suspend fun setApiKey(url: String, apiKey: String) {
        val normalizedUrl = Url(url).toString()
        secretsRepository.setApiKey(normalizedUrl, apiKey)
        keys[normalizedUrl] = apiKey
    }

    suspend fun deleteApiKey(url: String) {
        val normalizedUrl = Url(url).toString()
        secretsRepository.deleteApiKey(normalizedUrl)
        keys.remove(normalizedUrl)
    }
}
