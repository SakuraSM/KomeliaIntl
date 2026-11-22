package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.ReadingDesign
import snd.komelia.ui.LocalMacOS
import snd.komelia.ui.LocalMacToolbarSearch
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.MacDesign
import snd.komelia.ui.RegisterMacToolbarSearch
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.search.SearchTextField

/** Search uses the destination title row on Android and a persistent field elsewhere. */
@Composable
internal fun BrowseSearchHeader(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = title,
    onSubmit: (String) -> Unit = {},
    searchTopSpacing: Dp = 0.dp,
    titleStyle: TextStyle? = null,
    titleInset: Dp? = null,
    headerHeight: Dp? = null,
    searchContentPadding: Dp = 10.dp,
    searchPaintHeight: Dp? = null,
    titleOffset: Dp? = null,
    searchInsets: PaddingValues = PaddingValues(horizontal = ReadingDesign.browseInset),
    compactOnAndroid: Boolean = false,
    searchInitiallyExpanded: Boolean = false,
    titleActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) = KomeliaSafeTopBar {
    if (compactOnAndroid && LocalPlatform.current == PlatformType.MOBILE) {
        CompactBrowseSearchHeader(title, query, onQueryChange, placeholder, onSubmit, searchInitiallyExpanded, titleActions, actions)
        return@KomeliaSafeTopBar
    }
    val focus = LocalFocusManager.current
    val useMacToolbar = LocalMacOS.current && LocalMacToolbarSearch.current != null
    val searchField: @Composable () -> Unit = {
        SearchTextField(
            query = query,
            onQueryChange = onQueryChange,
            onDone = { focus.clearFocus(); onSubmit(it) },
            onDismiss = { onQueryChange("") },
            placeholder = placeholder,
            horizontalContentPadding = searchContentPadding,
            paintedHeight = searchPaintHeight,
            modifier = if (useMacToolbar) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().padding(searchInsets)
                .padding(top = searchTopSpacing, bottom = 4.dp),
            trailingContent = { Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
        )
    }
    if (useMacToolbar) RegisterMacToolbarSearch(searchField)
    Column {
        BrowsePageHeader(title = title, prominent = true,
            titleStyle = if (useMacToolbar) androidx.compose.material3.MaterialTheme.typography.headlineSmall else titleStyle,
            horizontalInset = if (useMacToolbar) MacDesign.pageInset else titleInset,
            headerHeight = if (useMacToolbar) 64.dp else headerHeight,
            titleOffset = if (useMacToolbar) 0.dp else titleOffset, actions = titleActions)
        if (!useMacToolbar) searchField()
    }
}

@Composable
private fun CompactBrowseSearchHeader(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSubmit: (String) -> Unit,
    initiallyExpanded: Boolean,
    titleActions: @Composable RowScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    var focusRequested by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }
    val closeSearch = {
        focusManager.clearFocus()
        keyboard?.hide()
        expanded = false
    }
    if (!expanded) {
        val iconOffset = ReadingDesign.destinationTitleOffset
        BrowsePageHeader(title = title, titleOffset = iconOffset) {
            titleActions()
            IconButton(
                onClick = { focusRequested = true; expanded = true },
                modifier = Modifier.semantics { selected = query.isNotBlank() },
            ) {
                Icon(
                    Icons.Rounded.Search,
                    stringResource(Res.string.navbar_search),
                    Modifier.offset(y = iconOffset),
                    tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            CompositionLocalProvider(LocalBrowseHeaderIconOffset provides iconOffset) { actions() }
        }
    } else {
        BackPressHandler(closeSearch)
        LaunchedEffect(focusRequested) {
            if (focusRequested) {
                fieldFocus.requestFocus()
                keyboard?.show()
                focusRequested = false
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = closeSearch) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
            }
            SearchTextField(
                query = query,
                onQueryChange = onQueryChange,
                onDone = { focusManager.clearFocus(); keyboard?.hide(); onSubmit(it) },
                onDismiss = { onQueryChange("") },
                onBack = closeSearch,
                placeholder = placeholder,
                paintedHeight = 40.dp,
                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
            )
            actions()
        }
    }
}
