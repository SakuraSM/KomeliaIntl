package snd.komelia.ui.settings.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.settings.CommonSettingsRepository
import snd.komelia.ui.LoadState
import snd.komelia.updates.AppRelease
import snd.komelia.updates.AppUpdater
import snd.komelia.updates.AppVersion
import snd.komelia.updates.StartupUpdateChecker
import snd.komelia.updates.UpdateProgress
import java.lang.reflect.Proxy
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateFlowTest {
    @Test fun emptyResponseCompletesAndCanBeRetried() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = SettingsFixture()
            val updater = FakeUpdater(emptyList())
            val vm = AppUpdatesViewModel(MutableStateFlow(emptyList()), updater, fixture.repository, AppNotifications())
            vm.initialize()
            vm.checkForUpdates()
            advanceUntilIdle()
            assertTrue(vm.state.value is LoadState.Success)
            assertNotNull(vm.lastUpdateCheck.value)
            updater.result = listOf(release())
            vm.checkForUpdates()
            advanceUntilIdle()
            assertEquals(release().version, vm.latestVersion.value)
            assertEquals(2, updater.calls)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedRequestStopsSpinnerAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fixture = SettingsFixture()
            val updater = FakeUpdater(listOf(release())).apply { failure = true }
            val vm = AppUpdatesViewModel(MutableStateFlow(emptyList()), updater, fixture.repository, AppNotifications())
            vm.initialize()
            vm.checkForUpdates()
            advanceUntilIdle()
            assertTrue(vm.state.value is LoadState.Success)
            assertNull(vm.lastUpdateCheck.value)
            assertTrue(vm.checkFailed.value)
            updater.failure = false
            vm.checkForUpdates()
            advanceUntilIdle()
            assertEquals(release().version, vm.latestVersion.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun startupHonorsSwitchAndDailyThrottle() = runTest {
        val fixture = SettingsFixture()
        val updater = FakeUpdater(listOf(release()))
        val checker = StartupUpdateChecker(updater, fixture.repository, MutableStateFlow(emptyList()))
        fixture.enabled.value = false
        assertNull(checker.checkForUpdates())
        fixture.enabled.value = true
        fixture.checked.value = Clock.System.now()
        assertNull(checker.checkForUpdates())
        assertEquals(0, updater.calls)
        fixture.checked.value = null
        assertEquals(release(), checker.checkForUpdates())
        assertEquals(1, updater.calls)
    }

    @Test fun dismissingAnOlderReleaseDoesNotHideANewerRelease() = runTest {
        val fixture = SettingsFixture()
        fixture.dismissed.value = AppVersion.current
        val checker = StartupUpdateChecker(FakeUpdater(listOf(release())), fixture.repository, MutableStateFlow(emptyList()))
        assertEquals(release(), checker.checkForUpdates())
        fixture.checked.value = null
        fixture.dismissed.value = release().version
        assertNull(checker.checkForUpdates())
    }

    private class FakeUpdater(var result: List<AppRelease>) : AppUpdater {
        var failure = false
        var calls = 0
        override suspend fun getReleases(): List<AppRelease> {
            calls++
            if (failure) error("Update service unavailable")
            return result
        }
        override suspend fun updateToLatest(): Flow<UpdateProgress>? = null
        override fun updateTo(release: AppRelease): Flow<UpdateProgress>? = null
    }

    private class SettingsFixture {
        val enabled = MutableStateFlow(true)
        val checked = MutableStateFlow<Instant?>(null)
        val latest = MutableStateFlow<AppVersion?>(null)
        val dismissed = MutableStateFlow<AppVersion?>(null)
        val repository = Proxy.newProxyInstance(
            CommonSettingsRepository::class.java.classLoader,
            arrayOf(CommonSettingsRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getCheckForUpdatesOnStartup" -> enabled
                "getLastUpdateCheckTimestamp" -> checked
                "getLastCheckedReleaseVersion" -> latest
                "getDismissedVersion" -> dismissed
                "putCheckForUpdatesOnStartup" -> { enabled.value = args[0] as Boolean; Unit }
                "putLastUpdateCheckTimestamp" -> { checked.value = args[0] as Instant; Unit }
                "putLastCheckedReleaseVersion" -> { latest.value = args[0] as AppVersion?; Unit }
                "putDismissedVersion" -> { dismissed.value = args[0] as AppVersion; Unit }
                else -> error("Unexpected settings call: ${method.name}")
            }
        } as CommonSettingsRepository
    }

    companion object {
        private fun release() = AppRelease(
            version = AppVersion.fromString("999.0.0"),
            publishDate = Instant.parse("2026-09-01T00:00:00Z"),
            releaseNotesBody = "Test release",
            htmlUrl = "https://example.invalid/releases/999.0.0",
            assetName = null,
            assetUrl = null,
        )
    }
}
