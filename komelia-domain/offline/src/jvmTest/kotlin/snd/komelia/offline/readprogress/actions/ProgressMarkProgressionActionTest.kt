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
    fun indexedLocalEpubMatchesAbsoluteResourceUrlAndInterpolatesProgress() {
        val positions = listOf(
            R2Locator("OPS/chapter one.xhtml", "application/xhtml+xml", locations = R2Location(progression = 0f, totalProgression = 0.2f)),
            R2Locator("OPS/chapter one.xhtml", "application/xhtml+xml", locations = R2Location(progression = 1f, totalProgression = 0.6f)),
        )
        val locator = R2Locator("http://komelia/api/v1/books/local-book/resource/OPS/chapter%20one.xhtml#start", "application/xhtml+xml",
            locations = R2Location(progression = 0.5f))
        assertEquals(0.4f, localEpubTotalProgression(positions, locator), absoluteTolerance = 0.000001f)
        assertEquals(0.6f, localEpubTotalProgression(positions, locator.copy(locations = R2Location(progression = 1f))), absoluteTolerance = 0.000001f)
    }

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
