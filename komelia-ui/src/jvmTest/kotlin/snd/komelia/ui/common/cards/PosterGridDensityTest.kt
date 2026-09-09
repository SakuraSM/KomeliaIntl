package snd.komelia.ui.common.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.itemlist.PlaceHolderLazyCardGrid
import snd.komelia.ui.komeliaLayoutSpec
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.WindowSizeClass
import kotlin.test.assertEquals

class PosterGridDensityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingCardSizeChangesTheActualMobileGrid() {
        val cardWidth = mutableStateOf(150.dp)
        lateinit var grid: LazyGridState
        compose.setContent {
            CompositionLocalProvider(
                LocalPlatform provides PlatformType.MOBILE,
                LocalWindowWidth provides WindowSizeClass.COMPACT,
                LocalKomeliaLayout provides komeliaLayoutSpec(PlatformType.MOBILE, WindowSizeClass.COMPACT),
            ) {
                MaterialTheme {
                    grid = rememberLazyGridState()
                    Box(Modifier.size(360.dp, 600.dp)) {
                        PlaceHolderLazyCardGrid(elements = 10, minSize = cardWidth.value, scrollState = grid)
                    }
                }
            }
        }
        fun assertColumns(expected: Int) {
            compose.waitForIdle()
            compose.runOnIdle {
                val items = grid.layoutInfo.visibleItemsInfo
                val firstRow = items.first().row
                assertEquals(expected, items.count { it.row == firstRow })
            }
        }
        assertColumns(5)
        compose.runOnIdle { cardWidth.value = 240.dp }
        assertColumns(3)
        compose.runOnIdle { cardWidth.value = 350.dp }
        assertColumns(2)
    }
}
