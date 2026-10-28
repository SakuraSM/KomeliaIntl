package snd.komelia.ui.common.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navigation_back
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.search.SearchTextField
import snd.komelia.ui.platform.BackPressHandler

/** Search replaces the title within the same row; closing it keeps the active query. */
@Composable
internal fun BrowseSearchHeader(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    searchVisible: Boolean = false,
    placeholder: String = title,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var editing by remember { mutableStateOf(searchVisible || query.isNotBlank()) }
    var focusInput by remember { mutableStateOf(false) }
    var focusSearchButton by remember { mutableStateOf(false) }
    val inputFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val closeSearch = {
        focus.clearFocus()
        focusInput = false
        focusSearchButton = true
        editing = false
    }
    if (editing && !searchVisible) BackPressHandler(closeSearch)
    BrowsePageHeader(
        title = if (!editing && query.isNotBlank()) "$title · $query" else title,
        onSearchClick = if (editing) null else ({ focusInput = true; editing = true }),
        searchButtonModifier = Modifier.focusRequester(searchFocus).onGloballyPositioned {
            if (focusSearchButton) {
                focusSearchButton = false
                searchFocus.requestFocus()
            }
        },
        navigationIcon = if (!editing || searchVisible) null else ({
            IconButton(onClick = closeSearch) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.navigation_back))
            }
        }),
        titleContent = if (!editing) null else ({ modifier ->
            SearchTextField(
                query = query,
                onQueryChange = onQueryChange,
                onDone = { focus.clearFocus() },
                onDismiss = { onQueryChange("") },
                modifier = modifier.focusRequester(inputFocus).onGloballyPositioned {
                    if (focusInput) {
                        focusInput = false
                        inputFocus.requestFocus()
                    }
                }.onPreviewKeyEvent {
                    if (!searchVisible && it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                        closeSearch()
                        true
                    } else false
                },
                placeholder = placeholder,
            )
        }),
        actions = actions,
    )
}
