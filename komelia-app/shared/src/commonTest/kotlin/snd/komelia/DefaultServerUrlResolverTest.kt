package snd.komelia

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import snd.komelia.settings.ServerConnectionStatus
import snd.komelia.settings.ServerUrlResolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultServerUrlResolverTest {
    @Test fun configuredInitialStateCannotPassReadinessBeforeCollectorStarts() = runTest {
        val resolver = DefaultServerUrlResolver(MutableStateFlow(PRIMARY), MutableStateFlow(LAN),
            MutableStateFlow(true), emptyFlow(), LanServerProbe { true }, backgroundScope)
        assertIs<ServerUrlResolution.Pending>(resolver.resolution.value)
        runCurrent()
        val resolved = assertIs<ServerUrlResolution.Resolved>(resolver.resolution.value)
        assertEquals(LAN, resolved.effectiveUrl)
        assertEquals(PRIMARY, resolved.primaryUrl)
    }

    @Test fun unreachableLanFallsBackToPrimaryAfterThreeSeconds() = runTest {
        val resolver = DefaultServerUrlResolver(MutableStateFlow(PRIMARY), MutableStateFlow(LAN),
            MutableStateFlow(true), emptyFlow(), LanServerProbe { awaitCancellation() }, backgroundScope)
        runCurrent(); advanceTimeBy(2_999); runCurrent()
        assertIs<ServerUrlResolution.Pending>(resolver.resolution.value)
        advanceTimeBy(1); runCurrent()
        assertEquals(PRIMARY, assertIs<ServerUrlResolution.Resolved>(resolver.resolution.value).effectiveUrl)
        assertIs<ServerConnectionStatus.LanUnavailable>(resolver.connectionStatus.value)
    }

    @Test fun emptyConfigurationAndDisabledSwitchNeverProbe() = runTest {
        var probes = 0
        val primary = MutableStateFlow("")
        val enabled = MutableStateFlow(true)
        val resolver = DefaultServerUrlResolver(primary, MutableStateFlow(LAN), enabled,
            emptyFlow(), LanServerProbe { probes++; true }, backgroundScope)
        assertIs<ServerUrlResolution.Unconfigured>(resolver.resolution.value)
        runCurrent()
        enabled.value = false; primary.value = PRIMARY; runCurrent()
        assertEquals(PRIMARY, assertIs<ServerUrlResolution.Resolved>(resolver.resolution.value).effectiveUrl)
        assertEquals(0, probes)
    }

    @Test fun cancelledLateProbeCannotOverwriteNewConfiguration() = runTest {
        val oldProbe = CompletableDeferred<Boolean>()
        val lan = MutableStateFlow(LAN)
        val resolver = DefaultServerUrlResolver(MutableStateFlow(PRIMARY), lan, MutableStateFlow(true),
            emptyFlow(), LanServerProbe { url ->
                if (url == LAN) withContext(NonCancellable) { oldProbe.await() } else false
            }, backgroundScope)
        runCurrent()
        lan.value = "http://new-lan.invalid"; runCurrent()
        val current = resolver.resolution.value
        assertEquals(PRIMARY, assertIs<ServerUrlResolution.Resolved>(current).effectiveUrl)
        oldProbe.complete(true); runCurrent()
        assertEquals(current, resolver.resolution.value)
        assertEquals(PRIMARY, resolver.effectiveServerUrl.value)
    }

    @Test fun connectivityRefreshInvalidatesThePreviousRoute() = runTest {
        val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var isReachable = true
        val resolver = DefaultServerUrlResolver(MutableStateFlow(PRIMARY), MutableStateFlow(LAN),
            MutableStateFlow(true), events, LanServerProbe { isReachable }, backgroundScope)
        runCurrent()
        val old = assertIs<ServerUrlResolution.Resolved>(resolver.resolution.value)
        isReachable = false; events.emit(Unit); runCurrent()
        val current = assertIs<ServerUrlResolution.Resolved>(resolver.resolution.value)
        assertTrue(current.generation > old.generation)
        assertEquals(PRIMARY, current.effectiveUrl)
    }

    @Test fun changedSettingsInvalidateCommitEvenBeforeCollectorRuns() = runTest {
        val primary = MutableStateFlow(PRIMARY)
        val resolver = DefaultServerUrlResolver(primary, MutableStateFlow(LAN), MutableStateFlow(false),
            emptyFlow(), LanServerProbe { true }, backgroundScope)
        runCurrent()
        val route = assertIs<ServerUrlResolution.Resolved>(resolver.resolution.value)
        assertTrue(resolver.isCurrent(route))
        primary.value = "http://new-primary.invalid"
        assertFalse(resolver.isCurrent(route))
    }

    private companion object {
        const val PRIMARY = "http://primary.invalid"
        const val LAN = "http://lan.invalid"
    }
}
