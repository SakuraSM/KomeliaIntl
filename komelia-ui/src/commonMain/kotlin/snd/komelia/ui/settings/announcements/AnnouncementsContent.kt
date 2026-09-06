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
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_announcements_server
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.DefaultDateTimeFormats.localDateFormat
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.platform.cursorForHand
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncement

@Composable
fun AnnouncementsContent(state: AnnouncementsState) {
    SettingsSection(title = stringResource(Res.string.settings_announcements_server)) {
        if (state.serverAnnouncements.isEmpty()) {
            SourceStatusText(Res.string.settings_announcements_empty)
        } else {
            state.serverAnnouncements.forEachIndexed { index, announcement ->
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
