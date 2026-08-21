package snd.komelia.ui.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_image_card_size
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language_chinese_simplified
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language_english
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language_system
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_theme
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.AppLanguage
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.strings.AppStrings
import snd.komelia.ui.strings.stringLabels
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsContent(
    cardWidth: Dp,
    onCardWidthChange: (Dp) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        DropdownChoiceMenu(
            label = { Text(stringResource(Res.string.settings_app_theme)) },
            selectedOption = LabeledEntry(currentTheme, stringResource(AppStrings.forAppTheme(currentTheme))),
            options = stringLabels(AppTheme.entries) { AppStrings.forAppTheme(it) },
            onOptionChange = { onThemeChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp)
        )

        HorizontalDivider()

        DropdownChoiceMenu(
            label = { Text(stringResource(Res.string.settings_app_language)) },
            selectedOption = LabeledEntry(currentLanguage, languageLabel(currentLanguage)),
            options = AppLanguage.entries.map { LabeledEntry(it, languageLabel(it)) },
            onOptionChange = { onLanguageChange(it.value) },
            inputFieldModifier = Modifier.widthIn(min = 250.dp),
        )

        HorizontalDivider()

        Text(stringResource(Res.string.settings_app_image_card_size), modifier = Modifier.padding(10.dp))
        Slider(
            value = cardWidth.value,
            onValueChange = { onCardWidthChange(it.roundToInt().dp) },
            steps = 19,
            valueRange = 150f..350f,
            colors = AppSliderDefaults.colors(),
            modifier = Modifier.cursorForHand().padding(end = 20.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("${cardWidth.value}")

            Card(
                Modifier
                    .width(cardWidth)
                    .aspectRatio(0.703f)
            ) {

            }
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> Res.string.settings_app_language_system
        AppLanguage.EN -> Res.string.settings_app_language_english
        AppLanguage.ZH_CN -> Res.string.settings_app_language_chinese_simplified
    }
)
