package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_done
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.reading_filter_display
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_close_settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_options
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.platform.PlatformType

/** One title-row entry point for view choices, sorting and filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseOptionsButton(
    active: Boolean = false,
    sheetContentHeight: Dp? = null,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val label = stringResource(Res.string.browse_options)
    val colors = MaterialTheme.colorScheme
    val layout = LocalKomeliaLayout.current
    val mobile = LocalPlatform.current == PlatformType.MOBILE
    val contentInset = if (mobile) 22.dp else layout.dialogContentPadding
    val preferredHeight = sheetContentHeight?.let { (it * LocalDensity.current.fontScale.coerceAtLeast(1f)).coerceAtMost(640.dp) }
    val anchorFocus = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    LaunchedEffect(expanded) {
        if (!expanded && hasOpened) anchorFocus.requestFocus()
    }
    val options: @Composable ColumnScope.() -> Unit = {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 640.dp)
                .then(if (mobile && preferredHeight != null) Modifier.height((preferredHeight - 22.dp).coerceAtLeast(0.dp)) else Modifier)
                .then(if (mobile) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(top = if (mobile) 0.dp else layout.dialogContentPadding, bottom = if (mobile) 10.dp else layout.dialogContentPadding),
        ) {
            Row(Modifier.fillMaxWidth().then(if (mobile) Modifier.height(44.dp) else Modifier)
                .padding(start = if (mobile) 26.dp else contentInset, end = if (mobile) 8.dp else contentInset),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.reading_filter_display), style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                    modifier = Modifier.weight(1f).offset(y = if (mobile) (-2).dp else 0.dp))
                IconButton(onClick = dismiss) { Icon(Icons.Rounded.Close, stringResource(Res.string.navigation_close_settings)) }
            }
            if (mobile) {
                Column(Modifier.weight(1f, fill = preferredHeight != null).verticalScroll(rememberScrollState())
                    .padding(start = contentInset, end = contentInset, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(layout.controlSpacing)) {
                    content(dismiss)
                }
            } else Column(Modifier.padding(horizontal = contentInset)) { content(dismiss) }
            if (mobile) Surface(onClick = dismiss, color = Color.Transparent,
                modifier = Modifier.padding(horizontal = contentInset).fillMaxWidth().heightIn(min = 48.dp).testTag("browse-options-done")) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("browse-options-done-paint")) {
                        Box(contentAlignment = Alignment.Center) { Text(stringResource(Res.string.reading_done)) }
                    }
                }
            }
        }
    }
    Box {
        IconButton(
            onClick = { hasOpened = true; expanded = true },
            modifier = Modifier.focusRequester(anchorFocus).semantics { selected = active },
        ) {
            BadgedBox(badge = { if (active) Badge() }) {
                Icon(Icons.Rounded.Tune, label)
            }
        }
        if (!mobile) {
            KomeliaDropdownMenu(expanded, dismiss, modifier = Modifier.width(360.dp), content = options)
        }
    }
    if (mobile && expanded) {
        KomeliaModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            content = options,
        )
    }
}
