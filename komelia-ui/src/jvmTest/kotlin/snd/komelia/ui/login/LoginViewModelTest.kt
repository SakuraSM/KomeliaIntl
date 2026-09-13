package snd.komelia.ui.login

import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import snd.komelia.AppNotifications
import snd.komelia.KomgaAuthenticationState
import snd.komelia.komga.api.KomgaLibraryApi
import snd.komelia.komga.api.KomgaUserApi
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.settings.SecretsRepository
import snd.komelia.settings.ServerUrlResolution
import snd.komelia.ui.LoadState
import snd.komelia.ui.platform.PlatformType
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.user.KomgaUser
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @Test fun cancellationWhenLoadingIsPublishedCannotStartAnUnownedRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture { USER }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.vm.state.collect { if (it is LoadState.Loading) fixture.vm.cancel() }
        }
        try {
            fixture.vm.initialize(); runCurrent()
            assertEquals(0, fixture.userCalls)
            assertNull(fixture.auth.authenticatedUser.value)
            assertIs<LoadState.Error>(fixture.vm.state.value)
        } finally { observer.cancel(); fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    @Test fun initialLoadingAndRepeatedClicksOwnOnlyOneRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        val fixture = Fixture { gate.await(); USER }
        try {
            fixture.vm.initialize()
            assertIs<LoadState.Loading>(fixture.vm.state.value)
            fixture.vm.initialize(); fixture.vm.retryAutoLogin(); runCurrent()
            assertEquals(1, fixture.userCalls)
            gate.complete(Unit); runCurrent()
            assertIs<LoadState.Success<Unit>>(fixture.vm.state.value)
            assertEquals(USER, fixture.auth.authenticatedUser.value)
        } finally { fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    @Test fun cancelDiscardsLateUserAndAllowsAFreshAttempt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val late = CompletableDeferred<Unit>()
        var calls = 0
        val fixture = Fixture { if (++calls == 1) withContext(NonCancellable) { late.await() }; USER }
        try {
            fixture.vm.initialize(); runCurrent(); fixture.vm.cancel()
            assertIs<LoadState.Error>(fixture.vm.state.value)
            fixture.vm.retryAutoLogin(); runCurrent()
            assertIs<LoadState.Success<Unit>>(fixture.vm.state.value)
            late.complete(Unit); runCurrent()
            assertEquals(2, calls)
            assertEquals(1, fixture.libraryCalls)
            assertIs<LoadState.Success<Unit>>(fixture.vm.state.value)
            assertNull(fixture.vm.autoLoginError)
        } finally { late.complete(Unit); fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    @Test fun disposedScreenCannotCommitLateLibraries() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val late = CompletableDeferred<Unit>()
        val fixture = Fixture(libraries = { withContext(NonCancellable) { late.await() }; emptyList() }) { USER }
        try {
            fixture.vm.initialize(); runCurrent(); fixture.vm.onDispose()
            late.complete(Unit); runCurrent()
            assertNull(fixture.auth.authenticatedUser.value)
            fixture.vm.retryAutoLogin(); runCurrent()
            assertEquals(1, fixture.userCalls)
        } finally { late.complete(Unit); fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    @Test fun pendingResolutionTimesOutToRetryableFormAtFifteenSeconds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(resolver = LoginTestResolver(ServerUrlResolution.Pending(0, PRIMARY))) { USER }
        try {
            fixture.vm.initialize(); runCurrent(); advanceTimeBy(14_999); runCurrent()
            assertIs<LoadState.Loading>(fixture.vm.state.value)
            advanceTimeBy(1); runCurrent()
            fixture.vm.state.first { it is LoadState.Error }
            assertEquals(0, fixture.userCalls)
            assertTrue(!fixture.vm.autoLoginError.isNullOrBlank())
            fixture.resolver.resolve(PRIMARY)
            fixture.vm.retryAutoLogin(); runCurrent()
            assertIs<LoadState.Success<Unit>>(fixture.vm.state.value)
        } finally { fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    @Test fun cancelWhileWaitingForResolutionNeverResurrectsOrReportsTimeout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(resolver = LoginTestResolver(ServerUrlResolution.Pending(0, PRIMARY))) { USER }
        try {
            fixture.vm.initialize(); runCurrent(); fixture.vm.cancel()
            fixture.resolver.resolve(PRIMARY); advanceTimeBy(20_000); runCurrent()
            assertIs<LoadState.Error>(fixture.vm.state.value)
            assertEquals(0, fixture.userCalls)
            assertNull(fixture.vm.autoLoginError)
            assertNull(fixture.auth.authenticatedUser.value)
        } finally { fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    @Test fun missingConfigurationReturnsFormWithoutReadingCookieOrNetwork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(primaryUrl = "") { USER }
        try {
            fixture.vm.initialize(); runCurrent()
            assertIs<LoadState.Error>(fixture.vm.state.value)
            assertEquals(0, fixture.userCalls)
            assertEquals(0, fixture.cookieCalls)
        } finally { fixture.vm.onDispose(); Dispatchers.resetMain() }
    }

    private class Fixture(
        val resolver: LoginTestResolver = LoginTestResolver(),
        primaryUrl: String = PRIMARY,
        libraries: suspend () -> List<KomgaLibrary> = { emptyList() },
        user: suspend () -> KomgaUser,
    ) {
        var userCalls = 0
        var libraryCalls = 0
        var cookieCalls = 0
        private val users = suspendProxy<KomgaUserApi> { name ->
            check(name.startsWith("getMe")); userCalls++; user()
        }
        private val libraryApi = suspendProxy<KomgaLibraryApi> { name ->
            check(name.startsWith("getLibraries")); libraryCalls++; libraries()
        }
        val auth = KomgaAuthenticationState(MutableStateFlow(users), MutableStateFlow(libraryApi),
            MutableStateFlow(null), resolver.effectiveServerUrl)
        val vm = LoginViewModel(
            settingsRepository = plainProxy<CommonSettingsRepository> { name ->
                when (name) {
                    "getServerUrl" -> flowOf(primaryUrl)
                    "getCurrentUser" -> flowOf("fixture")
                    else -> error("Unexpected settings operation: $name")
                }
            },
            secretsRepository = plainProxy<SecretsRepository> { cookieCalls++; "fixture-cookie" },
            komgaUserApi = flowOf(users), komgaLibraryApi = flowOf(libraryApi), komgaAuthState = auth,
            serverUrlResolver = resolver, notifications = AppNotifications(), platform = PlatformType.DESKTOP,
            offlineUserRepository = null, offlineServerRepository = null, offlineSettingsRepository = null,
            offlineLibraryApi = null, localLibraryManager = null, logJournalRepository = null,
        )
    }

    private companion object {
        const val PRIMARY = "http://primary.invalid"
        val USER = OfflineUser.ROOT_USER.toKomgaUser()
        @Suppress("UNCHECKED_CAST")
        inline fun <reified Type> plainProxy(crossinline block: (String) -> Any?): Type =
            Proxy.newProxyInstance(Type::class.java.classLoader, arrayOf(Type::class.java)) { _, method, _ -> block(method.name) } as Type

        @Suppress("UNCHECKED_CAST")
        inline fun <reified Type> suspendProxy(crossinline block: suspend (String) -> Any?): Type =
            Proxy.newProxyInstance(Type::class.java.classLoader, arrayOf(Type::class.java)) { _, method, args ->
                val continuation = args.last() as Continuation<Any?>
                val invoke: suspend () -> Any? = { block(method.name) }
                invoke.startCoroutineUninterceptedOrReturn(continuation)
            } as Type
    }
}
