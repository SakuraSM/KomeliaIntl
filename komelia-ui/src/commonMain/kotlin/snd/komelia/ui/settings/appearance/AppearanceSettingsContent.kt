package snd.komelia.ui.settings.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_card_density_hint
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_card_width_value
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_image_card_size
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language_chinese_simplified
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language_english
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_language_system
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_app_theme
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.AppLanguage
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.AppSliderDefaults
import snd.komelia.ui.common.components.LabeledEntry
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.common.itemlist.PlaceHolderLazyCardGrid
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.posterColumnCount
import snd.komelia.ui.settings.SettingsChoiceRow
import snd.komelia.ui.strings.AppStrings
import snd.komelia.ui.strings.stringLabels

@Composable
fun AppearanceSettingsContent(
    cardWidth: Dp,
    onCardWidthChange: (Dp) -> Unit,
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    surfaceStyle: AppSurfaceStyle,
    onSurfaceStyleChange: (AppSurfaceStyle) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparencyChange: (Boolean) -> Unit,
    hasSaveError: Boolean,
    onRetrySave: () -> Unit,
    onResetCardWidth: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        if (hasSaveError) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(Res.string.appearance_save_failed))
                    TextButton(onClick = onRetrySave) { Text(stringResource(Res.string.appearance_retry)) }
                }
            }
        }
        SettingsSection {
            SettingsChoiceRow(
                title = stringResource(Res.string.appearance_surface_style),
                selected = LabeledEntry(surfaceStyle, surfaceLabel(surfaceStyle)),
                options = AppSurfaceStyle.entries.map { LabeledEntry(it, surfaceLabel(it)) },
                onChange = { onSurfaceStyleChange(it.value) },
            )
            Text(stringResource(Res.string.appearance_surface_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchWithLabel(
                checked = reduceTransparency,
                onCheckedChange = onReduceTransparencyChange,
                label = { Text(stringResource(Res.string.appearance_reduce_transparency)) },
                supportingText = { Text(stringResource(Res.string.appearance_reduce_transparency_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (surfaceStyle == AppSurfaceStyle.LIQUID_GLASS && !LocalSurfaceAppearance.current.renderingSupported) {
                Text(stringResource(Res.string.appearance_solid_fallback), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            SettingsChoiceRow(
                title = stringResource(Res.string.settings_app_theme),
                selected = LabeledEntry(currentTheme, stringResource(AppStrings.forAppTheme(currentTheme))),
                options = stringLabels(AppTheme.entries) { AppStrings.forAppTheme(it) },
                onChange = { onThemeChange(it.value) },
            )
            HorizontalDivider()
            SettingsChoiceRow(
                title = stringResource(Res.string.settings_app_language),
                selected = LabeledEntry(currentLanguage, languageLabel(currentLanguage)),
                options = AppLanguage.entries.map { LabeledEntry(it, languageLabel(it)) },
                onChange = { onLanguageChange(it.value) },
            )
        }
        SettingsSection {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.settings_app_image_card_size), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onResetCardWidth) { Text(stringResource(Res.string.appearance_reset_density), style = MaterialTheme.typography.labelMedium) }
            }
            Slider(
                value = cardWidth.value.coerceIn(150f, 350f),
                onValueChange = { onCardWidthChange(it.roundToInt().dp) },
                steps = 19,
                valueRange = 150f..350f,
                colors = AppSliderDefaults.colors(),
                thumb = { AppSliderDefaults.Thumb() },
                track = { AppSliderDefaults.Track(it) },
                modifier = Modifier.cursorForHand().padding(end = 20.dp),
            )
            val densityGrid = posterColumnCount(LocalPlatform.current, LocalWindowWidth.current, cardWidth) != null
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (densityGrid) {
                    Text(stringResource(Res.string.settings_app_card_density_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(240.dp)) {
                        PlaceHolderLazyCardGrid(elements = 6, minSize = cardWidth)
                    }
                } else {
                    Text(stringResource(Res.string.settings_app_card_width_value, cardWidth.value.toInt()))
                    Card(Modifier.width(cardWidth).aspectRatio(0.703f)) {}
                }
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

@Composable
private fun surfaceLabel(style: AppSurfaceStyle): String = stringResource(
    when (style) {
        AppSurfaceStyle.CLASSIC -> Res.string.appearance_classic
        AppSurfaceStyle.LIQUID_GLASS -> Res.string.appearance_liquid_glass
    }
)
