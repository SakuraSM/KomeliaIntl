package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import snd.komelia.ui.ReadingDesign
import snd.komelia.ui.LocalMacOS
import snd.komelia.ui.LocalMacToolbarSearch
import snd.komelia.ui.MacDesign
import snd.komelia.ui.RegisterMacToolbarSearch
import snd.komelia.ui.search.SearchTextField

/** A persistent title and search row keep scope, query and view options in one place. */
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
    titleActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) = KomeliaSafeTopBar {
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
