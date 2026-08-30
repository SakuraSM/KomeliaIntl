package snd.komelia.ui.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cafe.adriel.voyager.core.screen.Screen
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_about_description
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_about_project_repository
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_about_source_code
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_about_upstream_repository
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_about_version
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_about
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.updates.AppVersion
import snd.komelia.updates.AppProjectMetadata

class AboutSettingsScreen : Screen {
    @Composable
    override fun Content() {
        val uriHandler = LocalUriHandler.current
        SettingsScreenContainer(stringResource(Res.string.settings_navigation_about)) {
            SettingsSection(title = "Komelia Intl") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LocalKomeliaLayout.current.itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.settings_about_version, AppVersion.current.toString()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(Res.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsSection(title = stringResource(Res.string.settings_about_source_code)) {
                AboutRepositoryRow(
                    title = stringResource(Res.string.settings_about_project_repository),
                    url = AppProjectMetadata.projectRepositoryUrl,
                    onClick = { uriHandler.openUri(AppProjectMetadata.projectRepositoryUrl) },
                )
                AboutRepositoryRow(
                    title = stringResource(Res.string.settings_about_upstream_repository),
                    url = AppProjectMetadata.upstreamRepositoryUrl,
                    onClick = { uriHandler.openUri(AppProjectMetadata.upstreamRepositoryUrl) },
                )
            }
        }
    }
}

@Composable
private fun AboutRepositoryRow(
    title: String,
    url: String,
    onClick: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = layout.minimumTouchTarget)
            .cursorForHand(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(layout.cardContentPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = url.removePrefix("https://"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
