package snd.komelia.ui.settings.komf.mangabaka

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_providers_database_download_date
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_providers_mangabaka_database
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_providers_mangabaka_database_checksum
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_providers_mangabaka_database_download
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komf_providers_mangabaka_database_update
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_display_metadata
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_display_metadata_hint
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.mangabaka_error
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import snd.komelia.DefaultDateTimeFormats.localDateFormat
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.komf.providers.DatabaseDownloadContent
import snd.komf.api.config.DownloadProgress
import snd.komf.api.config.MangaBakaDatabaseDto

@Composable
fun KomfMangaBakaSettingsContent(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    mangaBakaDbMetadata: MangaBakaDatabaseDto?,
    onMangaBakaUpdate: () -> Flow<DownloadProgress>,
    error: String?,
) {
    SettingsSection {
        SwitchWithLabel(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            label = { Text(stringResource(Res.string.mangabaka_display_metadata)) },
            supportingText = { Text(stringResource(Res.string.mangabaka_display_metadata_hint)) }
        )

        var showMangaBakaDownloadProgress by remember { mutableStateOf(false) }
        if (showMangaBakaDownloadProgress) {
            DatabaseDownloadContent(
                onMangaBakaUpdate,
                { showMangaBakaDownloadProgress = false })
        }
        HorizontalDivider()

        if (error != null) {
            Text(stringResource(Res.string.mangabaka_error), style = MaterialTheme.typography.titleLarge)
            Text(error)
        } else {
            Text(
                stringResource(Res.string.komf_providers_mangabaka_database),
                style = MaterialTheme.typography.titleLarge
            )
            Column {
                if (mangaBakaDbMetadata != null) {
                    val downloadDate = remember(mangaBakaDbMetadata) {
                        mangaBakaDbMetadata.downloadTimestamp.toLocalDateTime(TimeZone.currentSystemDefault())
                            .format(localDateFormat)
                    }
                    Text(stringResource(Res.string.komf_providers_database_download_date, downloadDate))
                    Text(
                        stringResource(
                            Res.string.komf_providers_mangabaka_database_checksum, mangaBakaDbMetadata.checksum.trim()
                        )
                    )
                }
                FilledTonalButton(
                    onClick = { showMangaBakaDownloadProgress = true },
                    modifier = Modifier.cursorForHand()
                ) {
                    Text(
                        if (mangaBakaDbMetadata != null) stringResource(Res.string.komf_providers_mangabaka_database_update)
                        else stringResource(Res.string.komf_providers_mangabaka_database_download)
                    )
                }
            }
        }

    }
}
