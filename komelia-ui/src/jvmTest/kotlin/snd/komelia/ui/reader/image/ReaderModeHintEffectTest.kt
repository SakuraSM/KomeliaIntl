package snd.komelia.ui.reader.image

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.createComposeRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.repository.ReaderSettingsRepositoryWrapper
import snd.komelia.settings.ImageReaderSettingsRepository
import snd.komelia.settings.model.ReaderType
import snd.komelia.ui.reader.image.common.ReaderModeHintEffect
import snd.komelia.ui.reader.image.common.readerModeHintSignature
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderModeHintEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sameConfigurationDoesNotReplayAcrossNewReadingSessions() {
        val preferences = HintPreferences()
        val repository = mutableStateOf(preferences.open())
        val session = mutableStateOf(0)
        var count = 0
        compose.setContent { key(session.value) { Hint(repository.value) { count++ } } }
        compose.runOnIdle { assertEquals(1, count); session.value++ }
        compose.runOnIdle { assertEquals(1, count); repository.value = preferences.open(); session.value++ }
        compose.runOnIdle { assertEquals(1, count); assertEquals(1, preferences.writes) }
    }

    @Test fun savedCompositionStateSurvivesRecreation() {
        val repository = HintPreferences().open()
        val visible = mutableStateOf(true)
        var registry = SaveableStateRegistry(null) { true }
        var saved: Map<String, List<Any?>> = emptyMap()
        var count = 0
        compose.setContent {
            if (visible.value) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                Hint(repository) { count++ }
            }
        }
        compose.runOnIdle { assertEquals(1, count); saved = registry.performSave(); visible.value = false }
        compose.runOnIdle { registry = SaveableStateRegistry(saved) { true }; visible.value = true }
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun waitsForInitializationAndOnlyNotifiesActualConfigurationChanges() {
        val preferences = HintPreferences()
        val repository = preferences.open()
        val ready = mutableStateOf(false)
        val signature = mutableStateOf(PAGED_LTR)
        val request = mutableStateOf<String?>(null)
        val locale = mutableStateOf("English")
        var count = 0
        compose.setContent {
            Text(locale.value)
            Hint(repository, ready.value, signature.value, request.value) { count++ }
        }
        compose.runOnIdle { assertEquals(0, count); assertEquals(0, preferences.writes); ready.value = true }
        compose.runOnIdle { assertEquals(1, count); request.value = "same-value-request"; locale.value = "中文" }
        compose.runOnIdle { assertEquals(1, count); signature.value = PAGED_RTL; request.value = "direction-change" }
        compose.runOnIdle { assertEquals(2, count); signature.value = readerModeHintSignature(ReaderType.PANELS, "RIGHT_TO_LEFT") }
        compose.runOnIdle { assertEquals(3, count); signature.value = PAGED_LTR; request.value = "return-to-original" }
        compose.runOnIdle { assertEquals(4, count); assertEquals(4, preferences.writes) }
    }

    @Test fun disablingPersistsAndReenablingWaitsForTheNextEntryOrChange() {
        val repository = HintPreferences().open()
        val session = mutableStateOf(0)
        val signature = mutableStateOf(PAGED_LTR)
        val request = mutableStateOf<String?>(null)
        var count = 0
        compose.setContent { key(session.value) { Hint(repository, signature = signature.value, request = request.value) { count++ } } }
        compose.runOnIdle {
            assertEquals(1, count)
            runBlocking { repository.putReaderModeHintsEnabled(false) }
            signature.value = PAGED_RTL; request.value = "disabled-change"
        }
        compose.runOnIdle { assertEquals(1, count); session.value++ }
        compose.runOnIdle { assertEquals(1, count); runBlocking { repository.putReaderModeHintsEnabled(true) } }
        compose.runOnIdle { assertEquals(1, count); session.value++; request.value = null }
        compose.runOnIdle { assertEquals(2, count); session.value++ }
        compose.runOnIdle { assertEquals(2, count) }
    }

    @Test fun delayedDisabledPreferencesNeverFlashAnInitialHint() {
        val preferences = HintPreferences(ImageReaderSettings(readerModeHintsEnabled = false))
        val repository = preferences.open()
        val loaded = CompletableDeferred<Unit>()
        var count = 0
        compose.setContent {
            Hint(repository, consume = { signature -> loaded.await(); repository.consumeReaderModeHint(signature) }) { count++ }
        }
        compose.runOnIdle { assertEquals(0, count); loaded.complete(Unit) }
        compose.runOnIdle { assertEquals(0, count); assertEquals(0, preferences.writes) }
    }

    @Test fun leavingBeforePreferencesLoadDoesNotPersistOrDisplayALateHint() {
        val preferences = HintPreferences()
        val repository = preferences.open()
        val loaded = CompletableDeferred<Unit>()
        val visible = mutableStateOf(true)
        var count = 0
        compose.setContent {
            if (visible.value) Hint(repository, consume = { signature -> loaded.await(); repository.consumeReaderModeHint(signature) }) { count++ }
        }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { loaded.complete(Unit) }
        compose.runOnIdle { assertEquals(0, count); assertEquals(0, preferences.writes); visible.value = true }
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun supersededConfigurationCannotDisplayOrOverwriteTheLatestOne() {
        val preferences = HintPreferences()
        val repository = preferences.open()
        val oldRequest = CompletableDeferred<Unit>()
        val signature = mutableStateOf(PAGED_LTR)
        var count = 0
        compose.setContent {
            Hint(repository, signature = signature.value, consume = { value ->
                if (value == PAGED_LTR) oldRequest.await()
                repository.consumeReaderModeHint(value)
            }) { count++ }
        }
        compose.runOnIdle { signature.value = PAGED_RTL }
        compose.runOnIdle { assertEquals(1, count); oldRequest.complete(Unit) }
        compose.runOnIdle { assertEquals(1, count); assertEquals(PAGED_RTL, preferences.saved.lastReaderModeHintSignature) }
    }

    @Test fun failedSaveIsReportedWithoutAcknowledgingPersistenceOrRepeatingOnRecomposition() {
        val preferences = HintPreferences().apply { failSave = true }
        val repository = preferences.open()
        val session = mutableStateOf(0)
        val ready = mutableStateOf(true)
        var count = 0
        val failures = mutableListOf<Throwable>()
        compose.setContent { key(session.value) { Hint(repository, ready = ready.value, onFailure = { failures += it }) { count++ } } }
        compose.runOnIdle { assertEquals(0, count); assertEquals(1, failures.size); assertNull(preferences.saved.lastReaderModeHintSignature); ready.value = false }
        compose.runOnIdle { ready.value = true }
        compose.runOnIdle { assertEquals(1, failures.size); preferences.failSave = false; session.value++ }
        compose.runOnIdle { assertEquals(1, count); assertEquals(PAGED_LTR, preferences.saved.lastReaderModeHintSignature) }
    }

    @Composable
    private fun Hint(
        repository: ImageReaderSettingsRepository,
        ready: Boolean = true,
        signature: String = PAGED_LTR,
        request: String? = null,
        consume: suspend (String) -> Boolean = repository::consumeReaderModeHint,
        onFailure: suspend (Throwable) -> Unit = { throw AssertionError("Unexpected hint failure", it) },
        onHint: suspend () -> Unit,
    ) {
        val last: MutableState<String?> = rememberSaveable { mutableStateOf(null) }
        ReaderModeHintEffect(ready, request, signature, last, consume, onFailure, onHint)
    }

    private class HintPreferences(initial: ImageReaderSettings = ImageReaderSettings()) {
        var saved = initial
        var writes = 0
        var failSave = false
        fun open(): ImageReaderSettingsRepository = ReaderSettingsRepositoryWrapper(SettingsStateWrapper(saved) {
            if (failSave) throw IOException("Synthetic preference write failure")
            saved = it
            writes++
        })
    }

    private companion object {
        const val PAGED_LTR = "PAGED:LEFT_TO_RIGHT"
        const val PAGED_RTL = "PAGED:RIGHT_TO_LEFT"
    }
}
