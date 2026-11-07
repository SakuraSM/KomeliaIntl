package snd.komelia.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_no_series
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_no_series_matching_filters
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_no_results_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import snd.komelia.ui.library.LibrarySeriesTabState.SeriesSort
import snd.komelia.ui.series.SeriesFilter
import snd.komga.client.book.KomgaReadStatus

class LibrarySeriesEmptyStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyViewDistinguishesSearchAndFiltersFromAnEmptyLibrary() {
        val filter = mutableStateOf(SeriesFilter.DEFAULT)
        val loading = mutableStateOf(false)
        val hasResults = mutableStateOf(false)
        val editing = mutableStateOf(false)
        val empty = runBlocking { getString(Res.string.library_no_series) }
        val filtered = runBlocking { getString(Res.string.library_no_series_matching_filters) }
        val search = runBlocking { getString(Res.string.search_no_results_title) }

        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 400.dp)) {
                    LibrarySeriesEmptyState(loading.value, hasResults.value, editing.value, filter.value)
                }
            }
        }

        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.runOnIdle { filter.value = SeriesFilter.DEFAULT.copy(sortOrder = SeriesSort.UPDATED_DESC) }
        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.runOnIdle { filter.value = SeriesFilter.DEFAULT.copy(readStatus = listOf(KomgaReadStatus.UNREAD)) }
        compose.onNodeWithText(filtered).assertIsDisplayed()
        compose.runOnIdle { filter.value = SeriesFilter.DEFAULT.copy(searchTerm = "missing") }
        compose.onNodeWithText(search).assertIsDisplayed()
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithText(search).assertDoesNotExist()
        compose.runOnIdle {
            loading.value = false
            hasResults.value = true
        }
        compose.onNodeWithText(search).assertDoesNotExist()
        compose.runOnIdle {
            hasResults.value = false
            editing.value = true
        }
        compose.onNodeWithText(search).assertDoesNotExist()
    }
}
