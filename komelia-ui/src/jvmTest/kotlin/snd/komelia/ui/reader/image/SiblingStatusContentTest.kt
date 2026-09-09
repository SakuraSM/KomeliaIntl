package snd.komelia.ui.reader.image

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.reader.image.common.SiblingStatusContent
import kotlin.test.assertEquals

class SiblingStatusContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedLookupShowsRetryAndNeverClaimsThereIsNoNextBook() {
        var retries = 0
        val busy = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                SiblingStatusContent(SiblingLoad.Failed(IllegalStateException("synthetic failure")),
                    next = true, retrying = busy.value, onRetry = { retries++; busy.value = true })
            }
        }
        compose.onNodeWithTag("reader-sibling-failure").assertIsDisplayed()
        compose.onNodeWithTag("reader-sibling-end").assertDoesNotExist()
        compose.onNodeWithTag("reader-sibling-retry").performClick()
        compose.onNodeWithTag("reader-sibling-retry").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
