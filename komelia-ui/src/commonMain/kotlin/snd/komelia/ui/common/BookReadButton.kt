package snd.komelia.ui.common

import snd.komelia.ui.common.components.KomeliaExposedDropdownMenu
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_read_button
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.book_read_button_incognito
import org.jetbrains.compose.resources.stringResource
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.platform.cursorForHand
import snd.komga.client.book.MediaProfile.EPUB
import snd.webview.webviewIsAvailable

@Composable
fun readIsSupported(book: KomeliaBook) = book.media.mediaProfile != EPUB || webviewIsAvailable()

@Composable
fun BookReadButton(
    modifier: Modifier = Modifier,
    onRead: () -> Unit,
    onIncognitoRead: (() -> Unit)? = null,
    onDropdownOpenChange: (Boolean) -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.tertiaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    label: String? = null,
    showIcon: Boolean = true,
    paintedHeight: Dp? = null,
    labelStyle: TextStyle? = null,
    leadingIconSize: Dp = 20.dp,
    iconSpacing: Dp = 8.dp,
    labelAutoSize: TextAutoSize? = null,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.semantics { role = Role.Button }.pointerHoverIcon(PointerIcon.Hand),
        color = if (paintedHeight == null) containerColor else Color.Transparent,
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
        if (paintedHeight != null) Box(Modifier.fillMaxWidth().height(paintedHeight)
            .background(containerColor, RoundedCornerShape(10.dp)).testTag("reading-primary-paint"))
        Row(
            Modifier.height(maxOf(LocalKomeliaLayout.current.minimumTouchTarget, paintedHeight ?: 0.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadButton(
                modifier = (if (label != null) Modifier.weight(1f) else Modifier).padding(horizontal = 5.dp).fillMaxHeight(),
                onRead = onRead,
                label = label,
                showIcon = showIcon,
                labelStyle = labelStyle,
                leadingIconSize = leadingIconSize,
                iconSpacing = iconSpacing,
                labelAutoSize = labelAutoSize,
            )
            if (onIncognitoRead != null) {
                VerticalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                IncognitoDropDown(
                    modifier = Modifier.widthIn(min = LocalKomeliaLayout.current.minimumTouchTarget).padding(end = 5.dp).fillMaxHeight(),
                    onIncognitoRead = onIncognitoRead,
                    onDropdownOpenChange = onDropdownOpenChange
                )
            }
        }
        }
    }
}

@Composable
private fun ReadButton(
    modifier: Modifier,
    onRead: () -> Unit,
    label: String?,
    showIcon: Boolean,
    labelStyle: TextStyle?,
    leadingIconSize: Dp,
    iconSpacing: Dp,
    labelAutoSize: TextAutoSize?,
) {
    Row(
        modifier = Modifier.clickable { onRead() }.then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (label == null) Spacer(Modifier.width(5.dp))
        if (showIcon) Icon(
            imageVector = Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(if (label == null) 24.dp else leadingIconSize),
        )
        if (showIcon) Spacer(Modifier.width(if (label == null) 10.dp else iconSpacing))
        Text(label ?: stringResource(Res.string.book_read_button),
            style = labelStyle ?: if (label == null) LocalTextStyle.current else MaterialTheme.typography.labelLarge,
            autoSize = labelAutoSize,
            maxLines = if (labelAutoSize == null) Int.MAX_VALUE else 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncognitoDropDown(
    modifier: Modifier,
    onIncognitoRead: () -> Unit,
    onDropdownOpenChange: (Boolean) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = {
            onDropdownOpenChange(it)
            isExpanded = it
        },
    ) {

        Box(
            modifier = Modifier
                .clickable { isExpanded = true }
                .menuAnchor(PrimaryNotEditable)
                .then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ExpandMore, null)
        }
        KomeliaExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = {
                onDropdownOpenChange(false)
                isExpanded = false
            },
            modifier = Modifier.width(150.dp)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.book_read_button_incognito)) },
                onClick = { onIncognitoRead() },
                modifier = Modifier.cursorForHand()
            )
        }
    }
}
