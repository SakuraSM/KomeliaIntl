package snd.komelia.ui.settings.imagereader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.color.ColorCorrectionType
import snd.komelia.color.DefaultColorCorrection
import snd.komelia.ui.LoadState
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LabeledEntry

@Composable
fun DefaultColorCorrectionSettings(state: DefaultColorCorrectionState, onRetry: () -> Unit) {
    when (val loadState = state.state.collectAsState().value) {
        is LoadState.Error -> ErrorContent(loadState.exception, onReload = onRetry)
        LoadState.Uninitialized, LoadState.Loading -> Text(stringResource(Res.string.color_default_loading))
        is LoadState.Success -> DefaultColorCorrectionContent(
            selected = state.selected.collectAsState().value,
            presets = state.presets.collectAsState().value,
            isSaving = state.isSaving.collectAsState().value,
            onSelect = state::select,
        )
    }
}

@Composable
fun DefaultColorCorrectionContent(
    selected: DefaultColorCorrection?,
    presets: List<DefaultColorCorrection>,
    isSaving: Boolean,
    onSelect: (DefaultColorCorrection?) -> Unit,
) {
    val offLabel = stringResource(Res.string.color_default_off)
    val curvesLabel = stringResource(Res.string.color_correction_curves)
    val levelsLabel = stringResource(Res.string.color_correction_levels)
    fun entry(value: DefaultColorCorrection?): LabeledEntry<DefaultColorCorrection?> =
        LabeledEntry(value, value?.let {
            val type = if (it.configuration.type == ColorCorrectionType.COLOR_CURVES) curvesLabel else levelsLabel
            "$type: ${it.presetName}"
        } ?: offLabel)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.color_default_description))
        DropdownChoiceMenu(
            selectedOption = entry(selected),
            options = listOf(entry(null)) + presets.map { entry(it) },
            onOptionChange = { if (!isSaving) onSelect(it.value) },
            enabled = !isSaving,
            label = { Text(stringResource(Res.string.color_default_title)) },
            inputFieldModifier = Modifier.fillMaxWidth(),
        )
        if (isSaving) Text(stringResource(Res.string.color_default_saving))
        if (presets.isEmpty()) Text(stringResource(Res.string.color_default_empty))
        Text(stringResource(Res.string.color_default_snapshot))
    }
}
