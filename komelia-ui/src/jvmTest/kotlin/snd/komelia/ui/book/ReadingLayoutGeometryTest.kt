package snd.komelia.ui.book

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.Theme
import snd.komelia.settings.model.AppTheme
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SurfaceAppearance
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.common.cards.ItemCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadata
import snd.komga.client.book.KomgaMediaStatus
import snd.komga.client.book.Media
import snd.komga.client.book.MediaProfile
import snd.komga.client.book.ReadProgress
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

class ReadingLayoutGeometryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun desktopReadingActionKeepsItsCyanAccentInsideGlassChrome() {
        compose.setContent {
            MaterialTheme(colorScheme = Theme.DARK.colorScheme) {
                CompositionLocalProvider(LocalWindowWidth provides WindowSizeClass.FULL,
                    LocalSurfaceAppearance provides SurfaceAppearance(style = AppSurfaceStyle.LIQUID_GLASS, theme = AppTheme.DARK)) {
                    Box(Modifier.width(720.dp)) { ReadingActionFooter(book("synthetic-book"), onRead = {}) }
                }
            }
        }
        val pixels = compose.onAllNodes(hasClickAction())[0].captureToImage().toPixelMap()
        val fill = pixels[12, pixels.height / 2]
        assertTrue(fill.red < .55f && fill.green > .75f && fill.blue > .9f,
            "The primary reading action must stay cyan, rather than inherit pale chrome text: $fill")
    }

    @Test fun readingActionUsesTheWholeFooterAndStillMarksNormalReading() {
        var markProgress: Boolean? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
                    LocalWindowWidth provides WindowSizeClass.COMPACT) {
                    Box(Modifier.width(390.dp)) {
                        ReadingActionFooter(book("synthetic-book"), onRead = { markProgress = it })
                    }
                }
            }
        }
        val action = compose.onAllNodes(hasClickAction())[0]
        val bounds = action.getUnclippedBoundsInRoot()
        assertEquals(195.dp, (bounds.left + bounds.right) / 2)
        assertEquals(342.dp, bounds.right - bounds.left)
        action.performClick()
        compose.runOnIdle { assertEquals(true, markProgress) }
    }

    @Test fun compactCoverKeepsItsSizeInsideTheWiderGridCell() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(111.dp)) {
                    ItemCard(coverWidth = 88.dp,
                        image = { Box(Modifier.fillMaxSize().testTag("cover")) },
                        content = { Text("Synthetic title") })
                }
            }
        }
        val bounds = compose.onNodeWithTag("cover", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(88.dp, bounds.right - bounds.left)
        assertEquals(132.dp, bounds.bottom - bounds.top)
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
