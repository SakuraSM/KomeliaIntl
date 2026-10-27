package snd.komelia.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalLibraries
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadata
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.book.Media
import snd.komga.client.book.MediaProfile
import snd.komga.client.book.ReadProgress
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

class ContinueReadingHeroLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun largeFontPlacesTheActionBelowTheCompleteBookTitle() = assertControls(360, 1.5f, true)
    @Test fun narrowPhonePlacesTheActionBelowTheTitle() = assertControls(320, 1f, true)
    @Test fun regularPhoneKeepsTheActionBesideTheTitle() = assertControls(412, 1f, false)

    private fun assertControls(width: Int, fontScale: Float, stacked: Boolean) {
        var readClicks = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalLibraries provides MutableStateFlow(emptyList()),
            ) {
                MaterialTheme {
                    Box(Modifier.width(width.dp)) {
                        HeroReadingControls(book("Chapter twelve")) { readClicks++ }
                    }
                }
            }
        }
        val title = compose.onNodeWithText("Synthetic series").getUnclippedBoundsInRoot()
        val subtitle = compose.onNodeWithText("Chapter twelve").getUnclippedBoundsInRoot()
        val action = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        if (stacked) {
            assertTrue(action.top >= subtitle.bottom, "Reading action must not squeeze the book title: $action / $subtitle")
            assertTrue(title.right - title.left >= 150.dp, "Title must retain useful width: $title")
        } else {
            assertTrue(action.left >= title.right, "Regular controls should remain side by side")
        }
        compose.onNode(hasClickAction()).performClick()
        assertEquals(1, readClicks)
    }

    private fun book(id: String): KomeliaBook {
        val timestamp = Instant.fromEpochMilliseconds(1_000)
        return KomeliaBook(
            id = KomgaBookId(id), seriesId = KomgaSeriesId("series"), seriesTitle = "Synthetic series",
            libraryId = KomgaLibraryId("library"), name = id, url = "synthetic://$id", number = 1,
            created = timestamp, lastModified = timestamp, fileLastModified = timestamp, sizeBytes = 20, size = "20 B",
            media = Media(KomgaMediaStatus.READY, "application/zip", 4, "", false, false, MediaProfile.DIVINA),
            metadata = KomgaBookMetadata(id, "", "1", 1f, null, emptyList(), emptyList(), "", emptyList(),
                false, false, false, false, false, false, false, false, false, timestamp, timestamp),
            readProgress = ReadProgress(2, false, timestamp, "test", "test", timestamp, timestamp),
            deleted = false, fileHash = "", oneshot = false, downloaded = true,
            localFileLastModified = timestamp, remoteFileUnavailable = false,
        )
    }
}
