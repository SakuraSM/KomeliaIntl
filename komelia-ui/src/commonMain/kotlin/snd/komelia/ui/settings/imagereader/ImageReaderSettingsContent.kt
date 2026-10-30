package snd.komelia.ui.settings.imagereader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_image_advanced
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_clear_cache
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_thumbnail_previews
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_thumbnail_previews_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_volume_key_navigation
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_mode_hints
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_mode_hints_desc
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsContent
import snd.komelia.ui.settings.imagereader.onnxruntime.OnnxRuntimeSettingsState
import snd.komelia.ui.settings.imagereader.onnxruntime.isOnnxRuntimeSupported

@Composable
fun ImageReaderSettingsContent(
    loadThumbnailPreviews: Boolean,
    onLoadThumbnailPreviewsChange: (Boolean) -> Unit,

    volumeKeysNavigation: Boolean,
    onVolumeKeysNavigationChange: (Boolean) -> Unit,

    readerModeHintsEnabled: Boolean?,
    isSavingReaderModeHints: Boolean,
    onReaderModeHintsChange: (Boolean) -> Unit,

    onCacheClear: () -> Unit,
    onnxRuntimeSettingsState: OnnxRuntimeSettingsState,
    advancedContent: @Composable () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection {
            val platform = LocalPlatform.current
            SwitchWithLabel(
                checked = readerModeHintsEnabled ?: false,
                enabled = readerModeHintsEnabled != null && !isSavingReaderModeHints,
                onCheckedChange = onReaderModeHintsChange,
                label = { Text(stringResource(Res.string.settings_image_mode_hints)) },
                supportingText = { Text(stringResource(Res.string.settings_image_mode_hints_desc)) },
            )
            SwitchWithLabel(
                checked = loadThumbnailPreviews,
                onCheckedChange = onLoadThumbnailPreviewsChange,
                label = { Text(stringResource(Res.string.settings_image_thumbnail_previews)) },
                supportingText = { Text(stringResource(Res.string.settings_image_thumbnail_previews_desc)) },
            )

            if (platform == PlatformType.MOBILE) {
                SwitchWithLabel(
                    checked = volumeKeysNavigation,
                    onCheckedChange = onVolumeKeysNavigationChange,
                    label = { Text(stringResource(Res.string.settings_image_volume_key_navigation)) },
                )
            }

            FilledTonalButton(
                onClick = onCacheClear,
            ) { Text(stringResource(Res.string.settings_image_clear_cache)) }

        }
        var advanced by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { advanced = !advanced }) { Text(stringResource(Res.string.reading_image_advanced)) }
        if (advanced) {
            advancedContent()
        if (isOnnxRuntimeSupported()) {
            SettingsSection {
                OnnxRuntimeSettingsContent(
                    executionProvider = onnxRuntimeSettingsState.currentExecutionProvider,
                    availableDevices = onnxRuntimeSettingsState.availableDevices,
                    deviceId = onnxRuntimeSettingsState.deviceId.collectAsState().value,
                    onDeviceIdChange = onnxRuntimeSettingsState::onDeviceIdChange,
                    upscaleMode = onnxRuntimeSettingsState.upscaleMode.collectAsState().value,
                    onUpscaleModeChange = onnxRuntimeSettingsState::onUpscaleModeChange,
                    upscalerTileSize = onnxRuntimeSettingsState.upscalerTileSize.collectAsState().value,
                    onUpscalerTileSizeChange = onnxRuntimeSettingsState::onTileSizeChange,
                    upscaleModelPath = onnxRuntimeSettingsState.upscaleModelPath.collectAsState().value,
                    onUpscaleModelPathChange = onnxRuntimeSettingsState::onUpscaleModelPathChange,
                    onOrtInstall = onnxRuntimeSettingsState::onInstallRequest,
                    mangaJaNaiIsInstalled = onnxRuntimeSettingsState.mangaJaNaiIsInstalled.collectAsState().value,
                    onMangaJaNaiDownload = onnxRuntimeSettingsState::onMangaJaNaiDownloadRequest,
                    panelModelIsDownloaded = onnxRuntimeSettingsState.panelModelIsDownloaded.collectAsState().value,
                    onPanelDetectionModelDownloadRequest = onnxRuntimeSettingsState::onPanelDetectionModelDownloadRequest

                )
            }
        }
        }
    }
}
