package snd.komelia.ui.color.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.color.BookColorCorrectionMode
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry

@Composable
fun BookColorCorrectionModeControl(mode: BookColorCorrectionMode, isSaving: Boolean, onChange: (BookColorCorrectionMode) -> Unit) {
    val options = listOf(
        LabeledEntry(BookColorCorrectionMode.INHERIT, stringResource(Res.string.color_book_inherit)),
        LabeledEntry(BookColorCorrectionMode.CUSTOM, stringResource(Res.string.color_book_custom)),
        LabeledEntry(BookColorCorrectionMode.DISABLED, stringResource(Res.string.color_book_disabled)),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DropdownChoiceMenu(
            selectedOption = options.first { it.value == mode },
            options = options,
            onOptionChange = { onChange(it.value) },
            enabled = !isSaving,
            label = { Text(stringResource(Res.string.color_book_mode)) },
            inputFieldModifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(Res.string.color_book_mode_description))
    }
}
