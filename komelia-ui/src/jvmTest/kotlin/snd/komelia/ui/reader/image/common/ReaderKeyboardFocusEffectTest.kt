package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ReaderKeyboardFocusEffectTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lateMeasuredPageContentReceivesNavigationKeys() {
        val ready = mutableStateOf(false)
        var nextPages = 0
        compose.setContent {
            val requester = remember { FocusRequester() }
            var hasFocus by remember { mutableStateOf(false) }
            Box(Modifier.size(360.dp, 600.dp).focusable().focusRequester(requester).onFocusChanged { hasFocus = it.hasFocus }) {
                if (ready.value) {
                    Box(Modifier.size(360.dp, 600.dp).onKeyEvent {
                        if (it.key == Key.DirectionRight && it.type == KeyEventType.KeyUp) {
                            nextPages++; true
                        } else false
                    }.focusable().testTag("page-keys"))
                }
            }
            ReaderKeyboardFocusEffect(ready.value, hasFocus, requester)
        }
        compose.waitForIdle()
        compose.runOnIdle { ready.value = true }
        compose.waitForIdle()
        compose.onNodeWithTag("page-keys").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(1, nextPages) }
    }
}
