package snd.komelia.ui.settings.imagereader.onnxruntime

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration.Companion.Underline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.dialog_cancel
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.dialog_confirm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_choose_version
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_cuda
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_directml
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_directml_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_download_cuda
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_download_cudnn
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_download_tensorrt
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_downloading
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_experimental_warning
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_install
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_install_error
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_restart_required
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_rocm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_tensorrt
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_tensorrt_desc
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_webgpu
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_webgpu_desc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider.CPU
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider.CUDA
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider.DirectML
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider.ROCm
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider.TENSOR_RT
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider.WEBGPU
import snd.komelia.ui.common.components.CheckboxWithLabel
import snd.komelia.ui.dialogs.AppDialog
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.updates.UpdateProgress

@Composable
fun OrtInstallDialog(
    show: Boolean,
    onInstallRequest: (OnnxRuntimeExecutionProvider) -> Flow<UpdateProgress>,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val coroutineScope = rememberCoroutineScope()
    var provider by remember { mutableStateOf<OnnxRuntimeExecutionProvider?>(null) }
    var updateProgress by remember { mutableStateOf<UpdateProgress?>(null) }

    var installError by remember { mutableStateOf<String?>(null) }
    var showPostInstallDialog by remember { mutableStateOf(false) }
    fun onInstall(provider: OnnxRuntimeExecutionProvider) {
        coroutineScope.launch {
            runCatching {
                onInstallRequest(provider)
                    .conflate()
                    .collect {
                        updateProgress = it
                        delay(100)
                    }
            }.onFailure { installError = "${it::class.simpleName} ${it.message}" }
            showPostInstallDialog = true
        }
    }

    if (!showPostInstallDialog) {
        AppDialog(
            modifier = Modifier.widthIn(max = 840.dp),
            header = {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (updateProgress == null)
                        Text(
                            stringResource(Res.string.settings_image_onnxruntime_choose_version),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    else
                        Text(
                            stringResource(Res.string.settings_image_onnxruntime_downloading),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    HorizontalDivider(Modifier.padding(top = 10.dp))
                }
            },
            content = {
                val progress = updateProgress
                if (progress != null) {
                    UpdateProgressContent(progress)
                } else {
                    OrtDownloadDialogContent(
                        chosenProvider = provider,
                        onProviderChoice = { provider = it },
                    )
                }
            },
            controlButtons = {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.cursorForHand(),
                        content = { Text(stringResource(Res.string.dialog_cancel)) }
                    )

                    FilledTonalButton(
                        enabled = provider != null && updateProgress == null,
                        onClick = { provider?.let { onInstall(it) } },
                        modifier = Modifier.cursorForHand(),
                    ) {
                        Text(stringResource(Res.string.settings_image_onnxruntime_install))
                    }
                }
            },
            onDismissRequest = { if (updateProgress == null) onDismiss() }
        )
    } else {
        RestartDialog(error = installError, onConfirm = { onDismiss() })
    }
}

@Composable
private fun OrtDownloadDialogContent(
    chosenProvider: OnnxRuntimeExecutionProvider?,
    onProviderChoice: (OnnxRuntimeExecutionProvider) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.tertiary)
                .padding(10.dp)
                .fillMaxWidth()
        ) {
            Text(
                stringResource(Res.string.settings_image_onnxruntime_experimental_warning),
                color = MaterialTheme.colorScheme.tertiaryContainer
            )
        }
        val uriHandler = LocalUriHandler.current
        val providers = remember { supportedOnnxRuntimeExecutionProviders() }
        providers.forEach { provider ->
            when (provider) {
                CUDA ->
                    CheckboxWithLabel(
                        checked = chosenProvider == CUDA,
                        onCheckedChange = { onProviderChoice(CUDA) },
                        labelAlignment = Alignment.Top,
                        label = {
                            Column {
                                Text(stringResource(Res.string.settings_image_onnxruntime_cuda))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(Res.string.settings_image_onnxruntime_download_cuda),
                                        color = MaterialTheme.colorScheme.secondary,
                                        textDecoration = Underline,
                                        modifier = Modifier
                                            .clickable { uriHandler.openUri("https://developer.nvidia.com/cuda-downloads") }
                                            .cursorForHand()
                                            .padding(horizontal = 5.dp),
                                    )
                                    Text(
                                        stringResource(Res.string.settings_image_onnxruntime_download_cudnn),
                                        color = MaterialTheme.colorScheme.secondary,
                                        textDecoration = Underline,
                                        modifier = Modifier
                                            .clickable { uriHandler.openUri("https://developer.nvidia.com/cudnn-downloads") }
                                            .cursorForHand()
                                            .padding(horizontal = 5.dp),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                TENSOR_RT -> CheckboxWithLabel(
                    checked = chosenProvider == TENSOR_RT,
                    onCheckedChange = { onProviderChoice(TENSOR_RT) },
                    labelAlignment = Alignment.Top,
                    label = {
                        Column {
                            Text(stringResource(Res.string.settings_image_onnxruntime_tensorrt))
                            Text(
                                stringResource(Res.string.settings_image_onnxruntime_tensorrt_desc),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(Res.string.settings_image_onnxruntime_download_cuda),
                                    color = MaterialTheme.colorScheme.secondary,
                                    textDecoration = Underline,
                                    modifier = Modifier
                                        .clickable { uriHandler.openUri("https://developer.nvidia.com/cuda-downloads") }
                                        .cursorForHand()
                                        .padding(horizontal = 5.dp),
                                )
                                Text(
                                    stringResource(Res.string.settings_image_onnxruntime_download_cudnn),
                                    color = MaterialTheme.colorScheme.secondary,
                                    textDecoration = Underline,
                                    modifier = Modifier
                                        .clickable { uriHandler.openUri("https://developer.nvidia.com/cudnn-downloads") }
                                        .cursorForHand()
                                        .padding(horizontal = 5.dp),
                                )
                                Text(
                                    stringResource(Res.string.settings_image_onnxruntime_download_tensorrt),
                                    color = MaterialTheme.colorScheme.secondary,
                                    textDecoration = Underline,
                                    modifier = Modifier
                                        .clickable { uriHandler.openUri("https://developer.nvidia.com/tensorrt") }
                                        .cursorForHand()
                                        .padding(horizontal = 5.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                ROCm -> CheckboxWithLabel(
                    checked = chosenProvider == ROCm,
                    onCheckedChange = { onProviderChoice(ROCm) },
                    label = { Text(stringResource(Res.string.settings_image_onnxruntime_rocm)) },
                    modifier = Modifier.fillMaxWidth()
                )

                DirectML -> CheckboxWithLabel(
                    checked = chosenProvider == DirectML,
                    onCheckedChange = { onProviderChoice(DirectML) },
                    labelAlignment = Alignment.Top,
                    label = {
                        Column {
                            Text(stringResource(Res.string.settings_image_onnxruntime_directml))
                            Text(
                                stringResource(Res.string.settings_image_onnxruntime_directml_desc),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                CPU -> CheckboxWithLabel(
                    checked = chosenProvider == CPU,
                    onCheckedChange = { onProviderChoice(CPU) },
                    label = { Text("CPU") },
                    modifier = Modifier.fillMaxWidth()
                )

                WEBGPU -> CheckboxWithLabel(
                    checked = chosenProvider == WEBGPU,
                    onCheckedChange = { onProviderChoice(WEBGPU) },
                    labelAlignment = Alignment.Top,
                    label = {
                        Column {
                            Text(stringResource(Res.string.settings_image_onnxruntime_webgpu))
                            Text(
                                stringResource(Res.string.settings_image_onnxruntime_webgpu_desc),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RestartDialog(
    error: String?,
    onConfirm: () -> Unit,
) {
    AppDialog(
        modifier = Modifier.widthIn(max = 600.dp),
        content = {
            Box(Modifier.padding(30.dp)) {
                if (error != null)
                    Text(stringResource(Res.string.settings_image_onnxruntime_install_error, error))
                else
                    Text(stringResource(Res.string.settings_image_onnxruntime_restart_required))
            }
        },
        controlButtons = {
            Box(modifier = Modifier.padding(bottom = 10.dp, end = 10.dp)) {
                FilledTonalButton(
                    onClick = onConfirm,
                    modifier = Modifier.cursorForHand(),
                ) {
                    Text(stringResource(Res.string.dialog_confirm))
                }
            }
        },
        onDismissRequest = { }
    )
}
