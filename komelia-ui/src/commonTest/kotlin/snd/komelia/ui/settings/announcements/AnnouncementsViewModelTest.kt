package snd.komelia.ui.settings.announcements

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import snd.komelia.komga.api.KomgaAnnouncementsApi
import snd.komelia.ui.LoadState
import snd.komga.client.announcements.KomgaJsonFeed
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AnnouncementsViewModelTest {
    @Test
    fun preservesServerContentAndOrderWithoutAnUpdateClient() = runTest {
        val feed = Json.decodeFromString<KomgaJsonFeed>("""{
            "version":"https://jsonfeed.org/version/1", "title":"Komga notifications", "home_page_url":null, "description":null,
            "items":[
                {"id":"server-2", "title":"Server maintenance", "content_html":"<p>Server notice</p>", "url":"https://example.invalid/notice", "summary":null, "date_modified":null, "author":null, "_komga":{"read":false}},
                {"id":"server-1", "title":"Komga update", "content_html":null, "url":null, "summary":null, "date_modified":null, "author":null, "_komga":{"read":true}}
            ]
        }""")
        val api = FakeApi { feed }
        val state = assertIs<LoadState.Success<AnnouncementsState>>(loadServerAnnouncements(api)).value
        assertEquals(feed.items, state.serverAnnouncements)
        assertEquals(1, api.calls)
    }

    @Test
    fun emptyServerFeedStaysEmpty() = runTest {
        val api = FakeApi {
            Json.decodeFromString<KomgaJsonFeed>("""{"version":"https://jsonfeed.org/version/1","title":"Komga","home_page_url":null,"description":null,"items":[]}""")
        }
        val state = assertIs<LoadState.Success<AnnouncementsState>>(loadServerAnnouncements(api)).value
        assertTrue(state.serverAnnouncements.isEmpty())
    }

    @Test
    fun serverFailureMustNotBeMaskedByAppReleaseNotes() = runTest {
        val failure = IllegalStateException("server unavailable")
        val result = loadServerAnnouncements(FakeApi { throw failure })
        assertSame(failure, assertIs<LoadState.Error>(result).exception)
    }

    @Test
    fun cancellationIsNotReportedAsServerFailure() = runTest {
        assertFailsWith<CancellationException> {
            loadServerAnnouncements(FakeApi { throw CancellationException("screen closed") })
        }
    }

    private class FakeApi(val response: suspend () -> KomgaJsonFeed) : KomgaAnnouncementsApi {
        var calls = 0
        override suspend fun getAnnouncements(): KomgaJsonFeed {
            calls++
            return response()
        }
        override suspend fun markAnnouncementsRead(announcements: List<KomgaAnnouncementId>) = Unit
    }
}
