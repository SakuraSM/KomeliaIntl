package snd.komelia.ui.settings.announcements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_empty
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_project_releases
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_server
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_source_unavailable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_upstream_releases
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_updates_release_date
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.DefaultDateTimeFormats.localDateFormat
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.platform.cursorForHand
import snd.komelia.updates.GithubRelease
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncement

@Composable
fun AnnouncementsContent(state: AnnouncementsState) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing)) {
        ReleaseSection(
            title = Res.string.settings_announcements_project_releases,
            releases = state.projectReleases,
            unavailable = AnnouncementSource.Project in state.unavailableSources,
        )
        ReleaseSection(
            title = Res.string.settings_announcements_upstream_releases,
            releases = state.upstreamReleases,
            unavailable = AnnouncementSource.Upstream in state.unavailableSources,
        )
        ServerAnnouncementSection(
            announcements = state.serverAnnouncements,
            unavailable = AnnouncementSource.Server in state.unavailableSources,
        )
    }
}

@Composable
private fun ReleaseSection(
    title: StringResource,
    releases: List<GithubRelease>,
    unavailable: Boolean,
) {
    SettingsSection(title = stringResource(title)) {
        when {
            unavailable -> SourceStatusText(Res.string.settings_announcements_source_unavailable)
            releases.isEmpty() -> SourceStatusText(Res.string.settings_announcements_empty)
            else -> releases.forEachIndexed { index, release ->
                if (index > 0) HorizontalDivider()
                ReleaseAnnouncement(release)
            }
        }
    }
}

@Composable
private fun ServerAnnouncementSection(
    announcements: List<KomgaAnnouncement>,
    unavailable: Boolean,
) {
    SettingsSection(title = stringResource(Res.string.settings_announcements_server)) {
        when {
            unavailable -> SourceStatusText(Res.string.settings_announcements_source_unavailable)
            announcements.isEmpty() -> SourceStatusText(Res.string.settings_announcements_empty)
            else -> announcements.forEachIndexed { index, announcement ->
                if (index > 0) HorizontalDivider()
                ServerAnnouncement(announcement)
            }
        }
    }
}

@Composable
private fun SourceStatusText(resource: StringResource) {
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun ReleaseAnnouncement(release: GithubRelease) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
        AnnouncementTitle(release.tagName, release.htmlUrl)
        val publishDate = remember(release.publishedAt) {
            release.publishedAt.toLocalDateTime(TimeZone.currentSystemDefault()).format(localDateFormat)
        }
        Text(
            text = stringResource(Res.string.settings_updates_release_date, publishDate),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (release.body.isNotBlank()) {
            SelectionContainer {
                val richTextState = rememberRichTextState()
                ConfigureRichText(richTextState)
                LaunchedEffect(release.body) { richTextState.setMarkdown(release.body) }
                RichText(richTextState)
            }
        }
    }
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun ServerAnnouncement(announcement: KomgaAnnouncement) {
    val layout = LocalKomeliaLayout.current
    Column(verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
        announcement.title?.let { title -> AnnouncementTitle(title, announcement.url) }
        announcement.dateModified?.let { date ->
            Text(
                text = date.toLocalDateTime(TimeZone.currentSystemDefault()).format(localDateFormat),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        announcement.contentHtml?.let { html ->
            SelectionContainer {
                val richTextState = rememberRichTextState()
                ConfigureRichText(richTextState)
                LaunchedEffect(html) { richTextState.setHtml(html) }
                RichText(richTextState)
            }
        }
    }
}

@OptIn(ExperimentalRichTextApi::class)
@Composable
private fun ConfigureRichText(state: RichTextState) {
    state.config.apply {
        linkColor = MaterialTheme.colorScheme.secondary
        linkTextDecoration = TextDecoration.Underline
        codeSpanBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
        codeSpanStrokeColor = MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
private fun AnnouncementTitle(title: String, url: String?) {
    val uriHandler = LocalUriHandler.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val onClickModifier = url?.let {
        Modifier
            .clickable(interactionSource = interactionSource, indication = null) { uriHandler.openUri(it) }
            .hoverable(interactionSource)
            .cursorForHand()
    } ?: Modifier

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        textDecoration = if (isHovered) TextDecoration.Underline else TextDecoration.None,
        modifier = onClickModifier,
    )
}
