package snd.komelia.ui.settings.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_check
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_check_on_startup
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_current_version
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_last_checked_date
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_last_checked_version
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_release_date
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_release_notes
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_update
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_external_hint
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_current
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_check_failed
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_empty
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import snd.komelia.DefaultDateTimeFormats.localDateFormat
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.common.components.SwitchWithLabel
import snd.komelia.ui.dialogs.update.UpdateProgressDialog
import snd.komelia.ui.platform.PlatformType
import snd.komelia.updates.AppRelease
import snd.komelia.updates.AppVersion
import snd.komelia.updates.UpdateProgress
import kotlin.time.Instant

@Composable
fun AppUpdatesContent(
    checkForUpdates: Boolean,
    onCheckForUpdatesChange: (Boolean) -> Unit,
    currentVersion: AppVersion,
    releases: List<AppRelease>,

    latestVersion: AppVersion?,
    lastChecked: Instant?,
    onCheckForUpdates: () -> Unit,
    versionCheckInProgress: Boolean,

    onUpdate: () -> Unit,
    onUpdateCancel: () -> Unit,
    downloadProgress: UpdateProgress?,
    installsInApp: Boolean,
    checkFailed: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {

        SwitchWithLabel(
            checked = checkForUpdates,
            onCheckedChange = onCheckForUpdatesChange,
            label = { Text(stringResource(Res.string.settings_updates_check_on_startup)) }
        )
        HorizontalDivider(Modifier.padding(bottom = 20.dp))
        VersionDetails(currentVersion, latestVersion, lastChecked, versionCheckInProgress)

        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {

            FilledTonalButton(
                onClick = { onCheckForUpdates() },
                enabled = !versionCheckInProgress,
            ) { Text(stringResource(Res.string.settings_updates_check)) }

            if (LocalPlatform.current != PlatformType.WEB_KOMF &&
                latestVersion != null && currentVersion < latestVersion
            ) {
                FilledTonalButton(
                    onClick = { onUpdate() },
                ) { Text(stringResource(Res.string.settings_updates_update)) }
            }
        }

        if (LocalPlatform.current == PlatformType.MOBILE && !installsInApp) {
            Text(stringResource(Res.string.settings_updates_external_hint), style = MaterialTheme.typography.bodySmall)
        }
        if (checkFailed) {
            Text(stringResource(Res.string.settings_updates_check_failed), color = MaterialTheme.colorScheme.error)
        }
        if (!versionCheckInProgress && !checkFailed && lastChecked != null) {
            when {
                latestVersion == null -> Text(stringResource(Res.string.settings_updates_empty))
                latestVersion <= currentVersion -> Text(stringResource(Res.string.settings_updates_current))
            }
        }

        if (releases.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            Text(
                stringResource(Res.string.settings_updates_release_notes),
                style = MaterialTheme.typography.headlineMedium
            )
            releases.forEach {
                ReleaseDetails(it)
                HorizontalDivider()
            }
        }

        if (downloadProgress != null) {
            UpdateProgressDialog(
                totalSize = downloadProgress.total,
                downloadedSize = downloadProgress.completed,
                onCancel = onUpdateCancel

            )
        }
    }
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun ReleaseDetails(release: AppRelease) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(release.version.toString(), style = MaterialTheme.typography.headlineMedium)
            val publishDate = remember {
                release.publishDate.toLocalDateTime(TimeZone.currentSystemDefault()).format(localDateFormat)
            }
            Text(
                stringResource(Res.string.settings_updates_release_date, publishDate),
                style = MaterialTheme.typography.labelLarge
            )
        }
        val state = rememberRichTextState()
        state.config.apply {
            linkColor = MaterialTheme.colorScheme.secondary
            linkTextDecoration = TextDecoration.Underline
            codeSpanBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
            codeSpanStrokeColor = MaterialTheme.colorScheme.surfaceVariant
        }
        remember { state.setMarkdown(release.releaseNotesBody) }
        RichText(state)
    }
}

@Composable
private fun VersionDetails(
    currentVersion: AppVersion,
    latestVersion: AppVersion?,
    lastChecked: Instant?,
    versionCheckInProgress: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.settings_updates_current_version), style = MaterialTheme.typography.labelLarge)
        Text("$currentVersion", style = MaterialTheme.typography.titleLarge)
        if (latestVersion != null) {
            Text(stringResource(Res.string.settings_updates_last_checked_version), style = MaterialTheme.typography.labelLarge)
            Text("$latestVersion", style = MaterialTheme.typography.titleLarge)
        }
        if (lastChecked != null) {
            val localDate = remember(lastChecked) {
                lastChecked.toLocalDateTime(TimeZone.currentSystemDefault()).format(localDateFormat)
            }
            Text(stringResource(Res.string.settings_updates_last_checked_date, localDate), style = MaterialTheme.typography.bodySmall)
        }
        if (versionCheckInProgress) CircularProgressIndicator(Modifier.size(24.dp))
    }
}
