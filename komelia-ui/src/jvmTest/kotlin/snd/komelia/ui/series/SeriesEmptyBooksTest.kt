package snd.komelia.ui.series

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_no_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.series_no_books_matching_filters
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaReferentialApi
import snd.komelia.ui.LoadState
import snd.komelia.ui.book.BooksFilterState
import snd.komelia.ui.common.menus.BookMenuActions
import snd.komelia.ui.series.SeriesBooksState.BooksData
import snd.komelia.ui.series.view.SeriesBooksContent
import snd.komga.client.book.KomgaReadStatus

class SeriesEmptyBooksTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyBooksDescribeActiveFilters() {
        val referentialApi = Proxy.newProxyInstance(
            KomgaReferentialApi::class.java.classLoader,
            arrayOf(KomgaReferentialApi::class.java),
        ) { _, method, _ -> error("Unexpected call to ${method.name}") } as KomgaReferentialApi
        val filters = BooksFilterState(MutableStateFlow(null), referentialApi, AppNotifications())
        val actions = BookMenuActions({}, {}, {}, {}, {}, {}, {})
        val empty = runBlocking { getString(Res.string.series_no_books) }
        val filtered = runBlocking { getString(Res.string.series_no_books_matching_filters) }

        compose.setContent {
            MaterialTheme {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.size(360.dp, 600.dp)) {
                    SeriesBooksContent(
                        series = null,
                        booksLoadState = LoadState.Success(BooksData()),
                        onBookClick = {},
                        onBookReadClick = { _, _ -> },
                        onBooksLayoutChange = {},
                        onBooksPageSizeChange = {},
                        onPageChange = {},
                        onBookSelect = {},
                        booksFilterState = filters,
                        bookContextMenuActions = actions,
                    )
                }
            }
        }

        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.runOnIdle { filters.onReadStatusSelect(KomgaReadStatus.UNREAD) }
        compose.onNodeWithText(filtered).assertIsDisplayed()
        compose.runOnIdle { filters.onReadStatusSelect(KomgaReadStatus.UNREAD) }
        compose.onNodeWithText(empty).assertIsDisplayed()
    }
}
