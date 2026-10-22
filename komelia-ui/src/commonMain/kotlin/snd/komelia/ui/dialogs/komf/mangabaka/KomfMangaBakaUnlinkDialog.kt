package snd.komelia.ui.dialogs.komf.mangabaka

import org.jetbrains.compose.resources.stringResource
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_unlink_confirm
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import snd.komelia.komga.api.model.KomeliaSeries
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.dialogs.ConfirmationDialog


@Composable
fun KomfMangaBakaUnlinkDialog(
    series: KomeliaSeries,
    onDismissRequest: () -> Unit,
) {
    val viewModelFactory = LocalViewModelFactory.current
    val vm = remember { viewModelFactory.getKomfMangaBakaUnlinkDialogViewModel(series, onDismissRequest) }
    DisposableEffect(Unit) { onDispose { vm.onDispose() } }
    ConfirmationDialog(
        body = stringResource(Res.string.mangabaka_unlink_confirm),
        onDialogConfirm = vm::onUnlink,
        dismissOnConfirm = false,
        onDialogDismiss = onDismissRequest
    )
}
