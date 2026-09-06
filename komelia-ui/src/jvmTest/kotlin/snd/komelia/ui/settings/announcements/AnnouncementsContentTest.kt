package snd.komelia.ui.settings.announcements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_empty
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_server
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.announcements.KomgaJsonFeed

class AnnouncementsContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun serverNoticeIsVisibleWithoutReleaseSections() {
        val feed = Json.decodeFromString<KomgaJsonFeed>("""{
            "version":"https://jsonfeed.org/version/1", "title":"Komga", "home_page_url":null,"description":null,
            "items":[{"id":"notice", "title":"SERVER NOTICE 57", "url":null,
                "content_html":"<p>Server maintenance notice</p>", "summary":null,
                "date_modified":null, "author":null, "_komga":{"read":false}}]
        }""")
        show(AnnouncementsState(feed.items))
        compose.onNodeWithText("SERVER NOTICE 57").assertExists()
        compose.onNodeWithText("Komelia Intl", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Upstream Komelia", substring = true).assertDoesNotExist()
    }

    @Test fun emptyFeedShowsServerHeadingAndEmptyMessage() {
        var heading = ""
        var empty = ""
        compose.setContent {
            MaterialTheme {
                heading = stringResource(Res.string.settings_announcements_server)
                empty = stringResource(Res.string.settings_announcements_empty)
                AnnouncementsContent(AnnouncementsState(emptyList()))
            }
        }
        compose.onNodeWithText(heading).assertExists()
        compose.onNodeWithText(empty).assertExists()
        compose.onNodeWithText("Komelia Intl", substring = true).assertDoesNotExist()
    }

    private fun show(state: AnnouncementsState) {
        compose.setContent {
            CompositionLocalProvider(
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
            ) {
                MaterialTheme { Box(Modifier.width(328.dp)) { AnnouncementsContent(state) } }
            }
        }
    }
}
