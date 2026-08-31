package snd.komelia.offline.readprogress.actions

import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
import snd.komga.client.user.KomgaUserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class ProgressMarkProgressionActionTest {
    @Test
    fun positionlessLocalEpubPersistsTheReaderLocator() {
        val locator = R2Locator(
            href = "chapter-01.xhtml",
            type = "application/xhtml+xml",
            locations = R2Location(
                progression = 0.25f,
                totalProgression = 0.1f,
            ),
        )
        val progression = R2Progression(
            modified = Instant.fromEpochMilliseconds(1_000),
            device = R2Device("test-device", "Test"),
            locator = locator,
        )

        val result = positionlessEpubProgress(
            bookId = KomgaBookId("local-book"),
            userId = KomgaUserId("local-user"),
            pageCount = 0,
            newProgression = progression,
        )

        assertEquals(locator, result.locator)
        assertEquals(0, result.page)
        assertFalse(result.completed)
    }
}
