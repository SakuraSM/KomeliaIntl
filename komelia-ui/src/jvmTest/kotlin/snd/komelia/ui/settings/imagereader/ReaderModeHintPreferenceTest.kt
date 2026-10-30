package snd.komelia.ui.settings.imagereader

import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.AppNotification
import snd.komelia.AppNotifications
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.repository.ReaderSettingsRepositoryWrapper
import snd.komelia.settings.ImageReaderSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderModeHintPreferenceTest {
    @Test fun leavingSettingsDuringAnAcceptedWriteStillPersistsTheDisabledPreference() = runTest {
        val writes = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val releaseSave = CompletableDeferred<Unit>()
        var stored = ImageReaderSettings()
        val repository = ReaderSettingsRepositoryWrapper(SettingsStateWrapper(stored) {
            releaseSave.await(); stored = it
        })
        val viewModel = viewModel(repository, AppNotifications(), writes)
        try {
            viewModel.initialize()
            viewModel.onReaderModeHintsChange(false)
            runCurrent()
            assertTrue(viewModel.isSavingReaderModeHints.value)
            writes.cancel()
            releaseSave.complete(Unit)
            advanceUntilIdle()
            assertFalse(stored.readerModeHintsEnabled)
            assertEquals(false, viewModel.readerModeHintsEnabled.value)
            assertFalse(viewModel.isSavingReaderModeHints.value)
        } finally {
            releaseSave.complete(Unit)
            writes.cancel()
        }
    }

    @Test fun failedDisableKeepsTheSavedChoiceAndReportsAnError() = runTest {
        val writes = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repository = ReaderSettingsRepositoryWrapper(SettingsStateWrapper(ImageReaderSettings()) {
            throw IOException("Synthetic preference save failure")
        })
        val notifications = AppNotifications()
        val viewModel = viewModel(repository, notifications, writes)
        try {
            viewModel.initialize()
            viewModel.onReaderModeHintsChange(false)
            advanceUntilIdle()
            assertEquals(true, viewModel.readerModeHintsEnabled.value)
            assertTrue(repository.getReaderModeHintsEnabled().first())
            assertFalse(viewModel.isSavingReaderModeHints.value)
            assertTrue(notifications.getNotifications().first().single() is AppNotification.Error)
        } finally {
            writes.cancel()
        }
    }

    private fun viewModel(settings: ImageReaderSettingsRepository, notifications: AppNotifications, scope: CoroutineScope) =
        ImageReaderSettingsViewModel(
            settingsRepository = settings, appNotifications = notifications,
            onnxRuntimeInstaller = null, onnxRuntime = null, upscaler = null, panelDetector = null,
            onnxModelDownloader = null, coilMemoryCache = null, coilDiskCache = null, readerDiskCache = null,
            colorCorrectionDependencies = DefaultColorCorrectionDependencies(settings, emptyPresets(), emptyPresets(), notifications),
            writeScope = scope,
        )

    private inline fun <reified Repository> emptyPresets(): Repository = Proxy.newProxyInstance(
        Repository::class.java.classLoader, arrayOf(Repository::class.java),
    ) { _, method, _ ->
        check(method.name == "getPresets")
        emptyList<Any>()
    } as Repository
}
