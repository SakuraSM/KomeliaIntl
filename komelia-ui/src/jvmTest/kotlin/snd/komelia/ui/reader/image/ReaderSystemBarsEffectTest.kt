package snd.komelia.ui.reader.image

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppWindowState
import snd.komelia.ui.reader.image.common.ReaderSystemBarsEffect
import kotlin.test.assertEquals

class ReaderSystemBarsEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readingHidesBothBarsAndControlsAndExitRestoreThem() {
        val window = RecordingWindowState()
        val controlsVisible = mutableStateOf(false)
        val readerVisible = mutableStateOf(true)
        compose.setContent {
            if (readerVisible.value) ReaderSystemBarsEffect(window, controlsVisible.value)
        }
        compose.runOnIdle {
            assertEquals(Request(true, true), window.lastRequest)
            controlsVisible.value = true
        }
        compose.runOnIdle {
            assertEquals(Request(false, false), window.lastRequest)
            controlsVisible.value = false
        }
        compose.runOnIdle {
            assertEquals(Request(true, true), window.lastRequest)
            readerVisible.value = false
        }
        compose.runOnIdle { assertEquals(Request(false, false), window.lastRequest) }
    }

    private data class Request(val fullscreen: Boolean, val hideNavigationBar: Boolean)

    private class RecordingWindowState : AppWindowState {
        override val isFullscreen = MutableStateFlow(false)
        var lastRequest: Request? = null

        override fun setFullscreen(enabled: Boolean, hideNavigationBar: Boolean) {
            isFullscreen.value = enabled
            lastRequest = Request(enabled, hideNavigationBar)
        }
    }
}
