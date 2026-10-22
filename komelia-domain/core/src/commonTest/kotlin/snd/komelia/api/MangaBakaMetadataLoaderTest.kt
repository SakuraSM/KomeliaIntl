package snd.komelia.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import snd.komelia.AppNotificationMessage
import snd.komelia.AppNotificationMessageKey
import snd.komelia.AppNotifications
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaBakaMetadataLoaderTest {
    @Test
    fun repeatedFailuresReportOnceUntilTheServiceRecovers() = runTest {
        val notifications = AppNotifications()
        val loader = MangaBakaMetadataLoader(notifications)
        repeat(3) {
            assertEquals("catalog", loader.load("catalog") { error("Synthetic failure") })
        }
        val failure = notifications.getNotifications().first().single()
        assertEquals(
            AppNotificationMessage.Localized(AppNotificationMessageKey.MANGABAKA_METADATA_UNAVAILABLE),
            failure.message,
        )
        notifications.remove(failure.id)
        assertEquals("metadata", loader.load("catalog") { "metadata" })
        loader.load("catalog") { error("Another failure") }
        assertEquals(1, notifications.getNotifications().first().size)
    }

    @Test
    fun metadataTimeoutReturnsTheCatalogWithinItsBudget() = runTest {
        val notifications = AppNotifications()
        val loader = MangaBakaMetadataLoader(notifications)
        assertEquals("catalog", loader.load("catalog") {
            delay(MANGABAKA_METADATA_TIMEOUT_MILLIS + 1)
            "metadata"
        })
        assertEquals(MANGABAKA_METADATA_TIMEOUT_MILLIS, testScheduler.currentTime)
        assertEquals(1, notifications.getNotifications().first().size)
    }

    @Test
    fun successfulMissingMetadataIsNotAnOutage() = runTest {
        val notifications = AppNotifications()
        val loader = MangaBakaMetadataLoader(notifications)
        assertNull(loader.load<String?>("unused") { null })
        assertTrue(notifications.getNotifications().first().isEmpty())
    }

    @Test
    fun cancellationPropagatesWithoutAnOutageNotification() = runTest {
        val notifications = AppNotifications()
        val loader = MangaBakaMetadataLoader(notifications)
        assertFailsWith<CancellationException> {
            loader.load("catalog") { throw CancellationException("Reader closed") }
        }
        assertTrue(notifications.getNotifications().first().isEmpty())
    }
}
