package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.settings.model.ContinuousReadingDirection
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.ui.reader.image.continuous.KeyMapState
import snd.komelia.ui.reader.image.continuous.continuousReaderOnKeyEvents
import snd.komelia.ui.reader.image.paged.pagedReaderOnKeyEvents as pagedKeys
import snd.komelia.ui.reader.image.panels.pagedReaderOnKeyEvents as panelKeys
import kotlin.test.assertEquals

class ReaderBackShortcutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pagedBackShortcutPreservesTheCurrentPage() = assertBackShortcut { previous ->
        { event ->
            pagedKeys(
                event = event,
                readingDirection = PagedReadingDirection.LEFT_TO_RIGHT,
                layoutOffset = false,
                onReadingDirectionChange = {},
                onScaleTypeCycle = {},
                onLayoutCycle = {},
                onChangeLayoutOffset = {},
                onPageChange = {},
                onMoveToLastPage = {},
                onMoveToNextPage = {},
                onMoveToPrevPage = previous,
                volumeKeysNavigation = false,
            )
        }
    }

    @Test fun panelBackShortcutPreservesTheCurrentPage() = assertBackShortcut { previous ->
        { event ->
            panelKeys(
                event = event,
                readingDirection = PagedReadingDirection.LEFT_TO_RIGHT,
                onReadingDirectionChange = {},
                onMoveToNextPage = {},
                onMoveToPrevPage = previous,
                volumeKeysNavigation = false,
            )
        }
    }

    @Test fun continuousBackShortcutPreservesTheCurrentPosition() {
        var shortcutKeyDownConsumed: Boolean? = null
        assertBackShortcut { previous ->
            val keys = KeyMapState(
                readingDirection = ContinuousReadingDirection.TOP_TO_BOTTOM,
                volumeKeysNavigation = false,
                scrollBy = { previous() },
                scrollForward = {},
                scrollBackward = previous,
                scrollToFirstPage = {},
                scrollToLastPage = {},
                changeReadingDirection = {},
            )
            val handleKey: (KeyEvent) -> Boolean = { event ->
                continuousReaderOnKeyEvents(event, keys).also { consumed ->
                    if (event.key == Key.DirectionLeft && event.isAltPressed && event.type == KeyEventType.KeyDown) {
                        shortcutKeyDownConsumed = consumed
                    }
                }
            }
            handleKey
        }
        assertEquals(true, shortcutKeyDownConsumed, "Reserve the shortcut until the exit handler receives KeyUp")
    }

    private fun assertBackShortcut(createHandler: (() -> Unit) -> (KeyEvent) -> Boolean) {
        var page = 2
        var exits = 0
        val handleKey = createHandler { page-- }
        compose.setContent {
            val requester = remember { FocusRequester() }
            Box(Modifier.size(400.dp).onKeyEvent { event ->
                if (event.key == Key.DirectionLeft && event.isAltPressed && event.type == KeyEventType.KeyUp) {
                    exits++
                    true
                } else false
            }) {
                Box(Modifier.size(400.dp).onKeyEvent(handleKey)
                    .focusRequester(requester).focusable().testTag("reader"))
            }
            LaunchedEffect(Unit) { requester.requestFocus() }
        }
        compose.onNodeWithTag("reader").performKeyInput {
            keyDown(Key.AltLeft)
            pressKey(Key.DirectionLeft)
            keyUp(Key.AltLeft)
        }
        compose.runOnIdle {
            assertEquals(1, exits, "Alt+Left must reach the reader exit handler")
            assertEquals(2, page, "Returning must not save a different reading position")
        }
        compose.onNodeWithTag("reader").performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(1, page, "Plain Left still turns the page") }
    }
}
