package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val label = stringResource(Res.string.browse_options)
    val layout = LocalKomeliaLayout.current
    val mobile = LocalPlatform.current == PlatformType.MOBILE
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
                .verticalScroll(rememberScrollState()).padding(layout.dialogContentPadding),
            verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            content(dismiss)
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
