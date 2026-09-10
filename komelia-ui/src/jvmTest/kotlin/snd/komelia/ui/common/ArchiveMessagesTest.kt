package snd.komelia.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import snd.komelia.offline.mediacontainer.LocalArchiveAccessException
import snd.komelia.offline.mediacontainer.LocalArchiveFailure
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveMessagesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyArchiveFailureHasAResource() {
        assertEquals(LocalArchiveFailure.entries.toSet(), ARCHIVE_FAILURE_MESSAGES.keys)
    }

    @Test fun wrappedArchiveErrorsUseTheLocalizedMessageInsteadOfTheChannelException() {
        var rendered = ""
        compose.setContent {
            MaterialTheme {
                rendered = archiveErrorMessage(IllegalStateException("Error reading Zip content from SafSeekableReadByteChannel",
                    LocalArchiveAccessException(LocalArchiveFailure.ENCRYPTED)))
                Text(rendered)
            }
        }
        compose.runOnIdle {
            assertTrue(rendered.isNotBlank())
            assertFalse(rendered.contains("SafSeekable"))
            assertFalse(rendered.contains("IllegalStateException"))
        }
    }
}
