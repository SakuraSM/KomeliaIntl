package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_panel_prerender
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_panel_prerender_description
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_panel_prerender_off
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout

@Composable
internal fun PanelPrerenderSettings(count: Int, onCountChange: (Int) -> Unit) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
        Text(stringResource(Res.string.reader_panel_prerender))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
            for (value in 0..MAX_PRERENDER_COUNT) {
                InputChip(
                    selected = count == value,
                    onClick = { onCountChange(value) },
                    label = {
                        Text(if (value == 0) stringResource(Res.string.reader_panel_prerender_off) else value.toString())
                    },
                )
            }
        }
        Text(stringResource(Res.string.reader_panel_prerender_description), style = MaterialTheme.typography.bodySmall)
    }
}

private const val MAX_PRERENDER_COUNT = 2
