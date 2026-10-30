package snd.komelia.ui.reader.image.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.settings.imagereader.ReaderModeHintPreference

@Composable
internal fun ReaderHintControls(preference: ReaderModeHintPreference?, volumeKeys: Boolean, onVolumeKeysChange: ((Boolean) -> Unit)?) {
    if (preference != null) {
        LaunchedEffect(preference) { preference.initialize() }
        val enabled = preference.enabled.collectAsState().value
        val isSaving = preference.isSaving.collectAsState().value
        SwitchWithLabel(checked = enabled ?: false, enabled = enabled != null && !isSaving,
            onCheckedChange = preference::change,
            label = { Text(stringResource(Res.string.settings_image_mode_hints)) },
            supportingText = { Text(stringResource(Res.string.settings_image_mode_hints_desc)) })
    }
    if (onVolumeKeysChange != null) SwitchWithLabel(checked = volumeKeys, onCheckedChange = onVolumeKeysChange,
        label = { Text(stringResource(Res.string.settings_image_volume_key_navigation)) })
}
