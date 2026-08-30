package snd.komelia.offline.sync.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OfflineLogEntryTest {
    @Test
    fun operationErrorsAreUsefulWithoutPersistingSensitiveExceptionMessages() {
        val entry = OfflineLogEntry.operationError(
            operation = OfflineLogEntry.Operation.USER_SWITCH,
            error = IllegalStateException(
                "https://komga.example/private?token=secret password=hunter2",
            ),
        )

        assertEquals(OfflineLogEntry.Type.ERROR, entry.type)
        assertContains(entry.message, "USER_SWITCH")
        assertContains(entry.message, "IllegalStateException")
        assertFalse(entry.message.contains("komga.example"))
        assertFalse(entry.message.contains("secret"))
        assertFalse(entry.message.contains("hunter2"))
    }
}
