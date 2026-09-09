package snd.komelia.ui.reader.image.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.reader.image.ReaderState
import snd.komelia.ui.reader.image.ReaderSibling
import snd.komelia.ui.reader.image.SiblingLoad

@Composable
internal fun ReaderSiblingContent(readerState: ReaderState, next: Boolean) {
    val books = readerState.booksState.collectAsState().value ?: return
    val retrying = readerState.retryingSibling.collectAsState().value
    val scope = rememberCoroutineScope()
    SiblingStatusContent(
        state = if (next) books.next else books.previous,
        next = next,
        retrying = retrying != null,
        onRetry = { scope.launch { readerState.retrySibling(next) } },
    )
}

@Composable
internal fun SiblingStatusContent(
    state: SiblingLoad<ReaderSibling>,
    next: Boolean,
    retrying: Boolean,
    onRetry: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is SiblingLoad.Available -> {
                Text(stringResource(if (next) Res.string.reader_next_book else Res.string.reader_previous_book),
                    style = MaterialTheme.typography.bodyMedium)
                Text(state.value.book.metadata.title, style = MaterialTheme.typography.titleLarge)
            }
            SiblingLoad.End -> Text(stringResource(if (next) Res.string.reader_no_next_book else Res.string.reader_no_previous_book),
                Modifier.testTag("reader-sibling-end"))
            is SiblingLoad.Failed -> {
                Text(stringResource(if (next) Res.string.reader_next_book_failed else Res.string.reader_previous_book_failed),
                    Modifier.testTag("reader-sibling-failure"))
                Button(onClick = onRetry, enabled = !retrying, modifier = Modifier.testTag("reader-sibling-retry")) {
                    Text(stringResource(if (retrying) Res.string.reader_sibling_retrying else Res.string.reader_sibling_retry))
                }
            }
        }
    }
}
