package snd.komelia.ui.settings.komf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_settings_text_discard
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_settings_text_edit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_settings_text_save
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.OutlinedHttpTextField
import snd.komelia.ui.common.components.PasswordTextField
import snd.komelia.ui.platform.WindowSizeClass

@Composable
fun SavableTextField(
    currentValue: String,
    onValueSave: (String) -> Unit,
    label: String,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    valueChangePolicy: (String) -> Boolean = { true },
    useEditButton: Boolean = false,
    isPassword: Boolean = false,
) {
    val motion = LocalKomeliaMotion.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (LocalWindowWidth.current == WindowSizeClass.COMPACT) 1 else Int.MAX_VALUE,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var isChanged by remember { mutableStateOf(false) }
        var editable by remember { mutableStateOf(!useEditButton) }
        var textFieldValue by remember(currentValue) { mutableStateOf(currentValue) }

        if (isPassword) {
            PasswordTextField(
                value = textFieldValue,
                enabled = editable,
                onValueChange = {
                    if (valueChangePolicy(it)) {
                        textFieldValue = it
                        isChanged = true
                    }
                },
                supportingText = supportingText,
                isError = isError,
                label = { Text(label) },
                modifier = Modifier.weight(1f).semantics { contentDescription = label }.animateContentSize(tween(motion.duration(motion.stateDurationMillis), easing = motion.standardEasing))
            )
        } else {
            OutlinedTextField(
                shape = RoundedCornerShape(10.dp),
                value = textFieldValue,
                enabled = editable,
                onValueChange = {
                    if (valueChangePolicy(it)) {
                        textFieldValue = it
                        isChanged = true
                    }
                },
                label = { Text(label) },
                supportingText = supportingText,
                isError = isError,
                singleLine = true,
                modifier = Modifier.weight(1f).semantics { contentDescription = label }.animateContentSize(tween(motion.duration(motion.stateDurationMillis), easing = motion.standardEasing))
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            if (!editable) {
                ElevatedButton(
                    onClick = {
                        editable = true
                        textFieldValue = ""
                    },
                ) {
                    Text(stringResource(Res.string.komf_settings_text_edit))
                }
            }

            AnimatedVisibility(editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ElevatedButton(
                        enabled = useEditButton || isChanged,
                        onClick = {
                            if (useEditButton) editable = false
                            isChanged = false
                            textFieldValue = currentValue
                        },
                    ) {
                        Text(stringResource(Res.string.komf_settings_text_discard))
                    }
                    FilledTonalButton(
                        onClick = {
                            onValueSave(textFieldValue)
                            if (useEditButton) editable = false
                            isChanged = false
                        },
                        enabled = isChanged,
                    ) {
                        Text(stringResource(Res.string.komf_settings_text_save))
                    }
                }
            }

        }
    }
}

@Composable
fun SavableHttpTextField(
    label: String,
    currentValue: String,
    onValueSave: (String) -> Unit,
    confirmationText: String = stringResource(Res.string.komf_settings_text_save),
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = if (LocalWindowWidth.current == WindowSizeClass.COMPACT) 1 else Int.MAX_VALUE,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var isChanged by remember { mutableStateOf(false) }
        var textFieldValue by remember(currentValue) { mutableStateOf(currentValue) }
        OutlinedHttpTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                isChanged = true
            },
            label = { Text(label) },
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            isError = isError,
            supportingText = supportingText
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ElevatedButton(
                enabled = isChanged,
                onClick = {
                    isChanged = false
                    textFieldValue = currentValue
                },
            ) {
                Text(stringResource(Res.string.komf_settings_text_discard))
            }
            FilledTonalButton(
                onClick = {
                    onValueSave(textFieldValue)
                    isChanged = false
                },
                enabled = isChanged || isError,
            ) {
                Text(confirmationText)
            }
        }
    }

}

val komfLanguageTagsSuggestions = listOf(
    LabeledEntry("en", "English (en)"),
    LabeledEntry("ja", "Japanese (ja)"),
    LabeledEntry("ja-ro", "Japanese romanized (ja-ro)"),
    LabeledEntry("ko", "Korean (ko)"),
    LabeledEntry("ko-ro", "Korean romanized (ko-ro)"),
    LabeledEntry("zh", "Simplified Chinese (zh)"),
    LabeledEntry("zh-hk", "Traditional Chinese (zh-hk)"),
    LabeledEntry("zh-ro", "Chinese romanized (zh-ro)"),
    LabeledEntry("pt-br", "Brazilian Portugese (pt-br)"),
    LabeledEntry("es", "Castilian Spanish (es)"),
    LabeledEntry("es-la", "Latin American Spanish (es-la)"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionField(
    label: String,
    languageValue: String,
    onLanguageValueChange: (String) -> Unit,
    onLanguageValueSave: () -> Unit,
) {
    var isChanged by remember { mutableStateOf(false) }
    val suggestedOptions = derivedStateOf {
        komfLanguageTagsSuggestions.filter { (_, label) -> label.lowercase().contains(languageValue.lowercase()) }
    }
    val focusManager = LocalFocusManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        var isExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = isExpanded,
            onExpandedChange = { isExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                shape = RoundedCornerShape(10.dp),
                value = languageValue,
                onValueChange = {
                    onLanguageValueChange(it)
                    isChanged = true
                },
                label = { Text(label) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label }
                    .menuAnchor(PrimaryEditable)
            )

            ExposedDropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false }
            ) {
                suggestedOptions.value.forEach {
                    DropdownMenuItem(
                        text = { Text(it.label) },
                        onClick = {
                            isExpanded = false
                            focusManager.clearFocus()
                            onLanguageValueChange(it.value)
                            onLanguageValueSave()
                            isChanged = false
                        }
                    )
                }

            }
        }
        FilledTonalButton(
            onClick = {
                onLanguageValueSave()
                isChanged = false
            },
            enabled = isChanged,
        ) {
            Text(stringResource(Res.string.komf_settings_text_save))
        }
    }
}
