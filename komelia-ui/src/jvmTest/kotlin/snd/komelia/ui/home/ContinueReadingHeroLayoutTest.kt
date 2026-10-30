package snd.komelia.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
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

    @Test fun largeFontKeepsProgressAboveAFullWidthReadingAction() = assertControls(360, 1.5f, true)
    @Test fun narrowPhoneKeepsAFullWidthReadingAction() = assertControls(320, 1f, true)
    @Test fun regularPhoneUsesTheSameReadingActionHierarchy() = assertControls(412, 1f, false)

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
        val progress = compose.onNode(hasText("50%", substring = true)).getUnclippedBoundsInRoot()
        val action = compose.onNode(hasClickAction()).getUnclippedBoundsInRoot()
        assertTrue(action.top >= progress.bottom, "Progress must remain above the reading action")
        assertEquals(width.dp, (action.right - action.left))
        assertTrue((action.bottom - action.top) >= 48.dp, "Reading action must retain its touch target")
        compose.onNode(hasClickAction()).performClick()
        assertEquals(1, readClicks)
    }

    @Test fun unknownProgressDoesNotRenderAnArbitraryPercentage() {
        compose.setContent {
            CompositionLocalProvider(LocalLibraries provides MutableStateFlow(emptyList())) {
                MaterialTheme {
                    val original = book("Unknown progress")
                    HeroReadingControls(original.copy(media = original.media.copy(pagesCount = 0))) {}
                }
            }
        }
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
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
