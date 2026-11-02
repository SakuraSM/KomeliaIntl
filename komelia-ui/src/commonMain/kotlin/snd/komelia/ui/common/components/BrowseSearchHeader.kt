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
    searchInsets: PaddingValues = PaddingValues(horizontal = ReadingDesign.browseInset),
    titleActions: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) = KomeliaSafeTopBar {
    val focus = LocalFocusManager.current
    Column {
        BrowsePageHeader(title = title, prominent = true, titleStyle = titleStyle, horizontalInset = titleInset, actions = titleActions)
        SearchTextField(
            query = query,
            onQueryChange = onQueryChange,
            onDone = { focus.clearFocus(); onSubmit(it) },
            onDismiss = { onQueryChange("") },
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().padding(searchInsets)
                .padding(top = searchTopSpacing, bottom = 4.dp),
            trailingContent = { Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
        )
    }
}
