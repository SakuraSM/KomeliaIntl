package snd.komelia.ui.reader.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_next_page
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.LocalMacOS
import snd.komelia.ui.KomeliaTheme
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.reader.image.common.PageSpreadProgressSlider
import snd.komga.client.book.KomgaBookId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacPageControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pageButtonsAndOptionalScrubberUseTheSamePageState() {
        val index = mutableStateOf(1)
        val visible = mutableStateOf(true)
        var pickerOpen = false
        val pages = (1..3).map { listOf(PageMetadata(KomgaBookId("synthetic"), it, IntSize(800, 1200))) }
        compose.setContent {
            CompositionLocalProvider(LocalMacOS provides true) {
                KomeliaTheme(AppTheme.DARK) {
                    if (visible.value) Box(Modifier.width(800.dp).background(Color.White)) {
                        PageSpreadProgressSlider(pages, index.value, { index.value = it }, true,
                            LayoutDirection.Ltr, onPagePickerVisibilityChange = { pickerOpen = it })
                    }
                }
            }
        }
        compose.onNodeWithText("2 / 3").assertExists()
        val pixels = compose.onNodeWithTag("mac-reader-page-controls").captureToImage().toPixelMap()
        assertTrue(pixels[pixels.width / 2, 2].luminance() < .3f, "Page controls need contrast over a white page")
        val next = runBlocking { getString(Res.string.reader_next_page) }
        compose.onNodeWithContentDescription(next).performClick()
        assertEquals(2, index.value)
        compose.onNodeWithText("3 / 3").performClick()
        compose.runOnIdle { assertTrue(pickerOpen) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertCountEquals(1)
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { assertFalse(pickerOpen) }
    }
}
