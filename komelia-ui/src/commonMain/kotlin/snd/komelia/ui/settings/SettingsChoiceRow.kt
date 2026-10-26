package snd.komelia.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry

@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    selected: LabeledEntry<T>,
    options: List<LabeledEntry<T>>,
    onChange: (LabeledEntry<T>) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val field: @Composable (Modifier) -> Unit = { modifier ->
            DropdownChoiceMenu(
                selectedOption = selected,
                options = options,
                onOptionChange = onChange,
                modifier = modifier,
                inputFieldModifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
            )
        }
        if (maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.2f) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                field(Modifier.weight(1.25f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                field(Modifier.fillMaxWidth())
            }
        }
    }
}
