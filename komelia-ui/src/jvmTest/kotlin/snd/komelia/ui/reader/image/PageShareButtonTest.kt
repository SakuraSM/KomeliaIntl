package snd.komelia.ui.reader.image

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_share_page
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.reader.image.settings.PageShareButton
import kotlin.test.assertEquals

class PageShareButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sharingHasAnAccessibleActionAndDisablesItDuringPreparation() {
        val preparing = mutableStateOf(false)
        var clicks = 0
        var label = ""
        compose.setContent {
            MaterialTheme {
                label = stringResource(Res.string.reader_share_page)
                PageShareButton(PageShareAction(preparing.value) { clicks++; preparing.value = true })
            }
        }
        compose.onNodeWithContentDescription(label).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(label).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
