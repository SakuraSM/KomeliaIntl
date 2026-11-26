package snd.komelia.ui.settings.imagereader.onnxruntime

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.dialog_cancel
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_cuda
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_directml
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_downloading
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_install
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_restart_required
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_rocm
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_tensorrt
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_image_onnxruntime_webgpu
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider
import snd.komelia.updates.UpdateProgress
import kotlin.test.assertEquals

class OrtInstallDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelOnlyDismissesTheDialogBeforeInstallationStarts() {
        val cancel = runBlocking { getString(Res.string.dialog_cancel) }
        val install = runBlocking { getString(Res.string.settings_image_onnxruntime_install) }
        val progress = runBlocking { getString(Res.string.settings_image_onnxruntime_downloading) }
        val restart = runBlocking { getString(Res.string.settings_image_onnxruntime_restart_required) }
        val provider = runBlocking {
            when (supportedOnnxRuntimeExecutionProviders().first()) {
                OnnxRuntimeExecutionProvider.CPU -> "CPU"
                OnnxRuntimeExecutionProvider.CUDA -> getString(Res.string.settings_image_onnxruntime_cuda)
                OnnxRuntimeExecutionProvider.TENSOR_RT -> getString(Res.string.settings_image_onnxruntime_tensorrt)
                OnnxRuntimeExecutionProvider.ROCm -> getString(Res.string.settings_image_onnxruntime_rocm)
                OnnxRuntimeExecutionProvider.DirectML -> getString(Res.string.settings_image_onnxruntime_directml)
                OnnxRuntimeExecutionProvider.WEBGPU -> getString(Res.string.settings_image_onnxruntime_webgpu)
            }
        }
        val releaseInstall = CompletableDeferred<Unit>()
        var installRequests = 0
        compose.setContent {
            MaterialTheme {
                var show by remember { mutableStateOf(false) }
                TextButton(onClick = { show = true }) { Text("Open test install dialog") }
                OrtInstallDialog(
                    show = show,
                    onInstallRequest = {
                        installRequests++
                        flow {
                            releaseInstall.await()
                            emit(UpdateProgress(1, 1))
                        }
                    },
                    onDismiss = { show = false },
                )
            }
        }

        val openDialog = compose.onNodeWithText("Open test install dialog")
        val cancelButton = compose.onNodeWithText(cancel)
        openDialog.performClick()
        cancelButton.assertIsDisplayed().performMouseInput { click(center) }
        cancelButton.assertDoesNotExist()

        openDialog.performClick()
        compose.onNodeWithText(provider).performClick()
        compose.onNodeWithText(install).performClick()
        cancelButton.assertIsNotEnabled()
        compose.onNodeWithText(install).assertIsNotEnabled()
        cancelButton.performMouseInput { click(center) }
        compose.onNodeWithText(progress).assertIsDisplayed()
        cancelButton.assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, installRequests)
            releaseInstall.complete(Unit)
        }
        compose.onNodeWithText(restart).assertIsDisplayed()
    }
}
