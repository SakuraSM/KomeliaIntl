package snd.komelia.ui

import androidx.compose.runtime.Composable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.window_error_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.window_logs_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun logsWindowTitle(): String = stringResource(Res.string.window_logs_title)

@Composable
fun errorWindowTitle(): String = stringResource(Res.string.window_error_title)
