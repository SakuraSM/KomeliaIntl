package snd.komelia.ui.login

import kotlinx.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.settings.ServerConnectionStatus
import snd.komelia.settings.ServerUrlResolution
import snd.komelia.settings.ServerUrlResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineLoginCoordinatorTest {
    @Test fun waitsForInitialResolutionAndUsesLanWithoutTryingSlowPrimary() = runTest {
        val resolver = LoginTestResolver(ServerUrlResolution.Pending(0, PRIMARY))
        val addresses = mutableListOf<String>()
        val login = OnlineLoginCoordinator(resolver, { addresses += resolver.effectiveServerUrl.value; USER }, { emptyList() })
        val result = async { login.login(PRIMARY) }
        runCurrent(); advanceTimeBy(2_000); runCurrent()
        assertTrue(addresses.isEmpty())
        resolver.resolve(LAN); runCurrent()
        assertEquals(USER, result.await().user)
        assertEquals(listOf(LAN), addresses)
    }

    @Test fun preparationAndRouteWaitShareTheFifteenSecondBudget() = runTest {
        val resolver = LoginTestResolver(ServerUrlResolution.Pending(0, PRIMARY))
        var calls = 0
        val login = OnlineLoginCoordinator(resolver, { calls++; USER }, { emptyList() })
        val result = async {
            withTimeoutOrNull(LOGIN_TOTAL_TIMEOUT_MILLIS) {
                delay(4_000) // Preparation is inside the same screen-owned deadline.
                login.login(PRIMARY)
            }
        }
        runCurrent(); advanceTimeBy(15_000); runCurrent()
        assertNull(result.await())
        assertEquals(0, calls)
    }

    @Test fun transientFailureRetriesOnlyOnceAfterBackoff() = runTest {
        var calls = 0
        val login = OnlineLoginCoordinator(LoginTestResolver(), {
            if (++calls == 1) throw IOException("connection interrupted")
            USER
        }, { emptyList() })
        val result = async { login.login(PRIMARY) }
        runCurrent(); assertEquals(1, calls)
        advanceTimeBy(299); runCurrent(); assertEquals(1, calls)
        advanceTimeBy(1); runCurrent()
        assertEquals(USER, result.await().user)
        assertEquals(2, calls)
    }

    @Test fun stalledRequestsStopAfterTwoSixSecondRounds() = runTest {
        var calls = 0
        val login = OnlineLoginCoordinator(LoginTestResolver(), { calls++; awaitCancellation() }, { emptyList() })
        val result = async { runCatching { login.login(PRIMARY) } }
        runCurrent(); advanceTimeBy(12_300); runCurrent()
        assertTrue(result.await().exceptionOrNull() is LoginTimeoutException)
        assertEquals(2, calls)
    }

    @Test fun manualCredentialsAreNotResubmittedOnConnectionFailure() = runTest {
        var calls = 0
        val credentials = LoginCredentials("fixture", "fixture")
        val login = OnlineLoginCoordinator(LoginTestResolver(), {
            assertEquals(credentials, it); calls++; throw IOException("interrupted")
        }, { emptyList() })
        assertFailsWith<IOException> { login.login(PRIMARY, credentials) }
        assertEquals(1, calls)
    }

    @Test fun invalidResponseDoesNotRetry() = runTest {
        var calls = 0
        val login = OnlineLoginCoordinator(LoginTestResolver(), { calls++; throw IllegalArgumentException("malformed response") }, { emptyList() })
        assertFailsWith<IllegalArgumentException> { login.login(PRIMARY) }
        assertEquals(1, calls)
    }

    @Test fun cancellationDoesNotRetryOrLoadLibraries() = runTest {
        var calls = 0
        var libraries = 0
        val login = OnlineLoginCoordinator(LoginTestResolver(), { calls++; awaitCancellation() }, { libraries++; emptyList() })
        val task = launch { login.login(PRIMARY) }
        runCurrent(); task.cancel(); runCurrent(); advanceTimeBy(20_000); runCurrent()
        assertEquals(1, calls)
        assertEquals(0, libraries)
    }

    @Test fun routeChangeCancelsRequestAndDiscardsLateUser() = runTest {
        val resolver = LoginTestResolver()
        val late = CompletableDeferred<Unit>()
        var libraries = 0
        val login = OnlineLoginCoordinator(resolver, { withContext(NonCancellable) { late.await() }; USER }, { libraries++; emptyList() })
        val result = async { runCatching { login.login(PRIMARY) } }
        runCurrent(); resolver.resolve(LAN); runCurrent(); late.complete(Unit); runCurrent()
        assertTrue(result.await().exceptionOrNull() is LoginRouteChangedException)
        assertEquals(0, libraries)
    }

    @Test fun routeChangeAfterUserBeforeLibrariesCannotProduceSession() = runTest {
        val resolver = LoginTestResolver()
        val late = CompletableDeferred<Unit>()
        val login = OnlineLoginCoordinator(resolver, { USER }, { withContext(NonCancellable) { late.await() }; emptyList() })
        val result = async { runCatching { login.login(PRIMARY) } }
        runCurrent(); resolver.resolve(LAN); runCurrent(); late.complete(Unit); runCurrent()
        assertTrue(result.await().isFailure)
    }

    @Test fun unconfiguredDoesNotMakeRequestsAndOfflineDoesNotWait() = runTest {
        var calls = 0
        val login = OnlineLoginCoordinator(LoginTestResolver(ServerUrlResolution.Unconfigured(0)), { calls++; USER }, { emptyList() })
        assertFailsWith<IllegalArgumentException> { login.login("") }
        assertEquals(0, calls)
        assertEquals(USER, login.loginOffline().user)
        assertEquals(1, calls)
    }

    private companion object {
        const val PRIMARY = "http://primary.invalid"
        const val LAN = "http://lan.invalid"
        val USER = OfflineUser.ROOT_USER.toKomgaUser()
    }
}

internal class LoginTestResolver(initial: ServerUrlResolution = ServerUrlResolution.Resolved(1, "http://primary.invalid", "http://primary.invalid")) : ServerUrlResolver {
    override val resolution = MutableStateFlow(initial)
    override val effectiveServerUrl = MutableStateFlow((initial as? ServerUrlResolution.Resolved)?.effectiveUrl ?: initial.primaryUrl)
    override val connectionStatus = MutableStateFlow<ServerConnectionStatus>(ServerConnectionStatus.Primary)
    override fun refresh() = Unit
    fun resolve(effectiveUrl: String) {
        effectiveServerUrl.value = effectiveUrl
        resolution.value = ServerUrlResolution.Resolved(resolution.value.generation + 1, resolution.value.primaryUrl, effectiveUrl)
    }
}
