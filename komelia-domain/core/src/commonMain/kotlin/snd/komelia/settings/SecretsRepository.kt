package snd.komelia.settings


interface SecretsRepository {

    suspend fun getCookie(url: String): String?

    suspend fun setCookie(url: String, cookie: String)

    suspend fun deleteCookie(url: String)

    suspend fun getApiKey(url: String): String?
    suspend fun setApiKey(url: String, apiKey: String)
    suspend fun deleteApiKey(url: String)

}