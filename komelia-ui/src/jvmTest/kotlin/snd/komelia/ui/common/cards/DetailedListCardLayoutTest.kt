package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass

class DetailedListCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sparseMetadataDoesNotExpandTheCardToTheOldMaximumHeight() = sparseCard(360)
    @Test fun largePhoneKeepsCompactCards() = sparseCard(412)
    @Test fun mediumWidthKeepsTheCoverAligned() = sparseCard(600, WindowSizeClass.MEDIUM, 180, 148)
    @Test fun expandedWidthKeepsTheCoverAligned() = sparseCard(840, WindowSizeClass.EXPANDED, 217, 185)
    @Test fun desktopWidthKeepsTheCoverAligned() = sparseCard(1280, WindowSizeClass.FULL, 217, 185)

    private fun sparseCard(
        width: Int,
        sizeClass: WindowSizeClass = WindowSizeClass.COMPACT,
        cardHeight: Int = 172,
        coverHeight: Int = 148,
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalWindowWidth provides sizeClass,
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, sizeClass),
            ) {
                MaterialTheme {
                    DetailedListCardLayout(
                        modifier = Modifier.width(width.dp).testTag("card"),
                        cover = { Box(Modifier.fillMaxSize().testTag("cover")) },
                        content = {
                            Column(Modifier.fillMaxHeight()) {
                                Text("A book title")
                                Text("247 pages")
                                Spacer(Modifier.weight(1f))
                            }
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("card").assertHeightIsEqualTo(cardHeight.dp)
        compose.onNodeWithTag("cover").assertHeightIsEqualTo(coverHeight.dp)
    }

    @Test fun coverStretchesWithTallMetadataAndActions() {
        compose.setContent {
            CompositionLocalProvider(
                LocalWindowWidth provides WindowSizeClass.COMPACT,
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
            ) {
                MaterialTheme {
                    DetailedListCardLayout(
                        modifier = Modifier.width(360.dp).testTag("card"),
                        cover = { Box(Modifier.fillMaxSize().testTag("cover")) },
                        content = { Box(Modifier.height(240.dp).testTag("details")) },
                    )
                }
            }
        }
        compose.onNodeWithTag("card").assertHeightIsEqualTo(264.dp)
        compose.onNodeWithTag("cover").assertHeightIsEqualTo(240.dp)
        compose.onNodeWithTag("details").assertHeightIsEqualTo(240.dp)
    }
}
