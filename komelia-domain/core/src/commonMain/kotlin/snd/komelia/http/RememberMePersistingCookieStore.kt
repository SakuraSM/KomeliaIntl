package snd.komelia.http

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.settings.SecretsRepository

@Deprecated("changed to komga-remember-me since komga 1.21.0")
private const val deprecatedRememberMeCookie = "remember-me"
private const val rememberMeCookie = "komga-remember-me"
private const val sessionCookie = "KOMGA-SESSION"

class RememberMePersistingCookieStore(
    private val komgaUrl: StateFlow<Url>,
    private val knownKomgaUrls: StateFlow<List<Url>>,
    private val secretsRepository: SecretsRepository,
) : CookiesStorage {
    private val delegate = AcceptAllCookiesStorage()

    suspend fun loadRememberMeCookie() {
        knownKomgaUrls.value.forEach { url ->
            secretsRepository.getCookie(url.toString())
                ?.let { parseServerSetCookieHeader(it) }
                ?.let { addCookieToKnownUrls(url, it) }
        }
    }

    /**
     *
     * if cookie manually added as part of request then it'll be updated with request's path
     * SSE reconnection will reuse the request with cookie headers
     * which will in turn override cookie with new path, breaking all other requests
     * as a workaround, skip cookie if its path doesn't equal to '/'
     * see [io.ktor.client.plugins.cookies.HttpCookies.captureHeaderCookies]
     */
    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if ((cookie.name == rememberMeCookie || cookie.name == sessionCookie) && cookie.path != komgaUrl.value.encodedPath.ifBlank { "/" }) {
            return
        }

        if (isKnownKomgaUrl(requestUrl)) addCookieToKnownUrls(requestUrl, cookie)
        else delegate.addCookie(requestUrl, cookie)

        if (
            (cookie.name == rememberMeCookie || cookie.name == deprecatedRememberMeCookie)
            && cookie.value.isNotBlank()
            && isKnownKomgaUrl(requestUrl)
        ) {
            secretsRepository.setCookie(komgaUrl.value.toString(), renderSetCookieHeader(cookie))
        }

    }

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val cookies = delegate.get(requestUrl)
        if (cookies.isNotEmpty() || !isKnownKomgaUrl(requestUrl)) return cookies

        return knownKomgaUrls.value
            .flatMap { delegate.get(it) }
            .distinctBy { it.name }
            .map { it.copy(domain = null, path = requestUrl.encodedPath.ifBlank { "/" }) }
    }

    override fun close() {
        delegate.close()
    }

    private suspend fun addCookieToKnownUrls(requestUrl: Url, cookie: Cookie) {
        knownKomgaUrls.value
            .forEach { url ->
                delegate.addCookie(url, cookie.copy(domain = null, path = url.encodedPath.ifBlank { "/" }))
            }
    }

    private fun isKnownKomgaUrl(url: Url): Boolean {
        return knownKomgaUrls.value.any { it.host == url.host && it.port == url.port }
    }
}
