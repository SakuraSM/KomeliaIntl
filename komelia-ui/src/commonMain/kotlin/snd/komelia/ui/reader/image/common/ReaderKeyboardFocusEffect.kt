package snd.komelia.ui.reader.image.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester

@Composable
internal fun ReaderKeyboardFocusEffect(contentReady: Boolean, hasFocus: Boolean, requester: FocusRequester) {
    // The page key handler is not composed until the reader has measured its viewport.
    if (!contentReady) return
    LaunchedEffect(hasFocus) {
        if (!hasFocus) requester.requestFocus()
    }
}
