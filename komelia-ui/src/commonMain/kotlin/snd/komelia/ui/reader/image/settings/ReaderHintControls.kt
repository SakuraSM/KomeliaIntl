package snd.komelia.ui.reader.image.settings

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.settings.imagereader.ReaderModeHintPreference

@Composable
internal fun ReaderHintControls(preference: ReaderModeHintPreference?, volumeKeys: Boolean, onVolumeKeysChange: ((Boolean) -> Unit)?, compact: Boolean = false) {
    val labelStyle = if (compact) MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp) else MaterialTheme.typography.bodyLarge
    if (preference != null) {
        LaunchedEffect(preference) { preference.initialize() }
        val enabled = preference.enabled.collectAsState().value
        val isSaving = preference.isSaving.collectAsState().value
        SwitchWithLabel(checked = enabled ?: false, enabled = enabled != null && !isSaving,
            onCheckedChange = preference::change,
            compactVisual = compact,
            indicatorOffsetY = if (compact) (-3).dp else 0.dp,
            label = { Text(stringResource(Res.string.settings_image_mode_hints), style = labelStyle) },
            supportingText = { Text(stringResource(Res.string.settings_image_mode_hints_desc),
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium) })
    }
    if (onVolumeKeysChange != null) SwitchWithLabel(checked = volumeKeys, onCheckedChange = onVolumeKeysChange,
        compactVisual = compact,
        label = { Text(stringResource(Res.string.settings_image_volume_key_navigation), style = labelStyle) })
}
