package snd.komelia.ui.color

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import snd.komelia.color.*
import snd.komelia.ui.color.view.BookColorCorrectionModeControl
import snd.komelia.ui.settings.imagereader.DefaultColorCorrectionContent
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultColorCorrectionContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsSelectsAPresetSnapshotAndCanDisableIt() {
        val selection = mutableStateOf<DefaultColorCorrection?>(null)
        val preset = DefaultColorCorrection("Test gamma", ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS))
        var off = ""
        var label = ""
        compose.setContent { MaterialTheme {
            off = stringResource(Res.string.color_default_off)
            label = stringResource(Res.string.color_correction_levels) + ": Test gamma"
            DefaultColorCorrectionContent(selection.value, listOf(preset), false) { selection.value = it }
        } }
        compose.onNodeWithText(off).performClick()
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(preset, selection.value) }
        compose.onNodeWithText(label).performClick()
        compose.onNodeWithText(off).performClick()
        compose.runOnIdle { assertNull(selection.value) }
    }

    @Test fun bookControlDistinguishesInheritCustomAndOff() {
        val mode = mutableStateOf(BookColorCorrectionMode.INHERIT)
        var inherit = ""; var custom = ""; var off = ""
        compose.setContent { MaterialTheme {
            inherit = stringResource(Res.string.color_book_inherit)
            custom = stringResource(Res.string.color_book_custom)
            off = stringResource(Res.string.color_book_disabled)
            BookColorCorrectionModeControl(mode.value, false) { mode.value = it }
        } }
        compose.onNodeWithText(inherit).performClick()
        compose.onNodeWithText(custom).performClick()
        compose.runOnIdle { assertEquals(BookColorCorrectionMode.CUSTOM, mode.value) }
        compose.onNodeWithText(custom).performClick()
        compose.onNodeWithText(off).performClick()
        compose.runOnIdle { assertEquals(BookColorCorrectionMode.DISABLED, mode.value) }
        compose.onNodeWithText(off).performClick()
        compose.onNodeWithText(inherit).performClick()
        compose.runOnIdle { assertEquals(BookColorCorrectionMode.INHERIT, mode.value) }
    }
}
