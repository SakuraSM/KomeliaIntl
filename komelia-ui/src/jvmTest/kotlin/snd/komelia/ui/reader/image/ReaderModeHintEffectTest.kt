package snd.komelia.ui.reader.image

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.reader.image.common.ReaderModeHintEffect
import kotlin.test.assertEquals

class ReaderModeHintEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedCompositionStateSurvivesRecreation() {
        val visible = mutableStateOf(true)
        var registry = SaveableStateRegistry(null) { true }
        var saved: Map<String, List<Any?>> = emptyMap()
        var count = 0
        compose.setContent {
            if (visible.value) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                val last = rememberSaveable { mutableStateOf<String?>(null) }
                ReaderModeHintEffect(true, null, last) { count++ }
            }
        }
        compose.runOnIdle { assertEquals(1, count); saved = registry.performSave(); visible.value = false }
        compose.runOnIdle { registry = SaveableStateRegistry(saved) { true }; visible.value = true }
        compose.runOnIdle { assertEquals(1, count) }
    }

    @Test fun rotationReentryDoesNotReplayButNewSessionDoes() {
        val visible = mutableStateOf(true)
        val savedHint = mutableStateOf<String?>(null)
        var count = 0
        compose.setContent { if (visible.value) ReaderModeHintEffect(true, null, savedHint) { count++ } }
        compose.runOnIdle { assertEquals(1, count); visible.value = false }
        compose.runOnIdle { visible.value = true }
        compose.runOnIdle { assertEquals(1, count); visible.value = false }
        compose.runOnIdle { savedHint.value = null; visible.value = true }
        compose.runOnIdle { assertEquals(2, count) }
    }

    @Test fun waitsForInitializationAndConsumesEachUserActionOnce() {
        val ready = mutableStateOf(false)
        val request = mutableStateOf<String?>(null)
        val savedHint = mutableStateOf<String?>(null)
        var count = 0
        compose.setContent { ReaderModeHintEffect(ready.value, request.value, savedHint) { count++ } }
        compose.runOnIdle { assertEquals(0, count); ready.value = true }
        compose.runOnIdle { assertEquals(1, count); ready.value = false; request.value = "mode-change" }
        compose.runOnIdle { assertEquals(1, count); ready.value = true }
        compose.runOnIdle { assertEquals(2, count); request.value = "mode-change" }
        compose.runOnIdle { assertEquals(2, count); request.value = "direction-change" }
        compose.runOnIdle { assertEquals(3, count) }
    }

    @Test fun restoredSessionAllowsNewRequestsAfterViewModelRecreation() {
        val visible = mutableStateOf(true)
        val request = mutableStateOf<String?>("old-request")
        val savedHint = mutableStateOf<String?>(null)
        var count = 0
        compose.setContent { if (visible.value) ReaderModeHintEffect(true, request.value, savedHint) { count++ } }
        compose.runOnIdle { assertEquals(1, count); visible.value = false }
        compose.runOnIdle { request.value = null; visible.value = true }
        compose.runOnIdle { assertEquals(1, count); request.value = "new-request" }
        compose.runOnIdle { assertEquals(2, count) }
    }
}
