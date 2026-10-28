package snd.komelia.ui.reader.image.settings

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import snd.komelia.ui.icons.AppIcons
import snd.komelia.ui.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reader_image_settings
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalKomeliaMotion
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.common.components.KomeliaIconButton
import snd.komelia.ui.common.components.komeliaChrome
import snd.komelia.ui.platform.WindowSizeClass.COMPACT
import snd.komelia.ui.reader.image.PageShareAction

@Composable
internal fun ImageReaderToolbar(
    book: KomeliaBook?,
    onBackPress: () -> Unit,
    onSettingsClick: () -> Unit,
    pageShareAction: PageShareAction? = null,
) {
    val layout = LocalKomeliaLayout.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .komeliaChrome(RoundedCornerShape(0.dp), MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = layout.pageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomeliaIconButton(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(Res.string.navigation_back),
            onClick = onBackPress,
            modifier = Modifier.size(layout.minimumTouchTarget),
        )
        if (book != null) {
            Column(Modifier.weight(1f).padding(horizontal = layout.controlSpacing)) {
                Text(
                    book.seriesTitle,
                    maxLines = 1,
                    style = if (LocalWindowWidth.current == COMPACT) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleLarge,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.metadata.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(iterations = if (LocalKomeliaMotion.current.isReducedMotion) 0 else Int.MAX_VALUE),
                )
            }
        } else Spacer(Modifier.weight(1f))
        pageShareAction?.let { PageShareButton(it) }
        KomeliaIconButton(
            imageVector = AppIcons.Outlined.Settings,
            contentDescription = stringResource(Res.string.reader_image_settings),
            onClick = onSettingsClick,
            modifier = Modifier.size(layout.minimumTouchTarget),
        )
    }
}
