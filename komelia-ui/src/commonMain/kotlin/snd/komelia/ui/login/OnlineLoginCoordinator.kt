package snd.komelia.ui.login

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import snd.komelia.settings.ServerUrlResolution
import snd.komelia.settings.ServerUrlResolver
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.user.KomgaUser

internal const val LOGIN_TOTAL_TIMEOUT_MILLIS = 15_000L
private const val LOGIN_ROUND_TIMEOUT_MILLIS = 6_000L
private const val LOGIN_RETRY_DELAY_MILLIS = 300L
private const val AUTO_LOGIN_MAX_ATTEMPTS = 2

internal class LoginTimeoutException : Exception("Login timed out")
internal class LoginRouteChangedException : Exception("Server configuration changed during login")
internal data class LoginCredentials(val username: String, val password: String)
internal data class LoginSession(
    val user: KomgaUser,
    val libraries: List<KomgaLibrary>,
    val route: ServerUrlResolution.Resolved?,
)

/** Acquires a complete session; only the owning screen task may commit it. */
internal class OnlineLoginCoordinator(
    private val resolver: ServerUrlResolver,
    private val loadUser: suspend (LoginCredentials?) -> KomgaUser,
    private val loadLibraries: suspend () -> List<KomgaLibrary>,
) {
    suspend fun login(primaryUrl: String, credentials: LoginCredentials? = null): LoginSession {
        require(primaryUrl.isNotBlank()) { "No server configured" }
        val attempts = if (credentials == null) AUTO_LOGIN_MAX_ATTEMPTS else 1
        repeat(attempts) { attempt ->
            try {
                val route = resolver.resolution.first {
                    it is ServerUrlResolution.Resolved && it.primaryUrl == primaryUrl
                } as ServerUrlResolution.Resolved
                return if (credentials != null) loginOnRoute(route, credentials)
                else withTimeoutOrNull(LOGIN_ROUND_TIMEOUT_MILLIS) { loginOnRoute(route, null) }
                    ?: throw LoginTimeoutException()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (attempt == attempts - 1 || !isTransientLoginFailure(error)) throw error
                delay(LOGIN_RETRY_DELAY_MILLIS)
            }
        }
        error("No login attempt executed")
    }

    fun requireCurrentRoute(session: LoginSession) {
        if (session.route != null && !resolver.isCurrent(session.route)) throw LoginRouteChangedException()
    }

    suspend fun loginOffline(): LoginSession = loadSession(null, null)

    private suspend fun loginOnRoute(
        route: ServerUrlResolution.Resolved,
        credentials: LoginCredentials?,
    ): LoginSession = coroutineScope {
        val routeChanged = async { resolver.resolution.first { it != route } }
        val session = async { loadSession(route, credentials) }
        try {
            select {
                routeChanged.onAwait { throw LoginRouteChangedException() }
                session.onAwait { result -> requireCurrentRoute(result); result }
            }
        } finally {
            routeChanged.cancel()
            session.cancel()
        }
    }

    private suspend fun loadSession(
        route: ServerUrlResolution.Resolved?,
        credentials: LoginCredentials?,
    ): LoginSession {
        if (route != null && !resolver.isCurrent(route)) throw LoginRouteChangedException()
        val user = loadUser(credentials)
        currentCoroutineContext().ensureActive()
        if (route != null && !resolver.isCurrent(route)) throw LoginRouteChangedException()
        val libraries = loadLibraries()
        currentCoroutineContext().ensureActive()
        return LoginSession(user, libraries, route).also(::requireCurrentRoute)
    }
}

internal fun isTransientLoginFailure(error: Exception): Boolean = when (error) {
    is NoTransformationFoundException -> false
    is ResponseException -> error.response.status.value in setOf(502, 503, 504)
    is LoginTimeoutException, is HttpRequestTimeoutException, is IOException -> true
    else -> false
}
