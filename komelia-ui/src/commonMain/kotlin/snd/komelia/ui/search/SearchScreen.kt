package snd.komelia.ui.search

import androidx.compose.material3.Text
import snd.komelia.ui.common.components.BrowseSearchHeader
import snd.komelia.ui.common.components.BrowseOptionsButton
import snd.komelia.ui.common.components.DropdownChoiceMenu
import snd.komelia.ui.common.components.LabeledEntry
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.browse_content_type
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_series_tab
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.search_books_tab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.common.components.BrowsePageHeader
import snd.komelia.ui.common.components.KomeliaPageScaffold
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.PlatformType
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.series.seriesScreen

class SearchScreen(
    private val initialQuery: String?,
) : ReloadableScreen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(initialQuery) {
            viewModelFactory.getSearchViewModel()
        }
        LaunchedEffect(initialQuery) { vm.initialize(initialQuery) }

        val navigator = LocalNavigator.currentOrThrow

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            KomeliaPageScaffold(topBar = {
                if (LocalPlatform.current == PlatformType.MOBILE) {
                    BrowseSearchHeader(
                        title = stringResource(Res.string.navbar_search),
                        query = vm.query,
                        onQueryChange = { vm.query = it },
                        searchVisible = true,
                    ) { SearchViewOptions(vm) }
                } else {
                    BrowsePageHeader(stringResource(Res.string.navbar_search)) { SearchViewOptions(vm) }
                }
            }) { topInset ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(top = topInset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (val state = vm.state.collectAsState().value) {
                        is LoadState.Error -> ErrorContent(
                            exception = state.exception,
                            onReload = vm::reload
                        )

                        LoadState.Uninitialized, LoadState.Loading -> LoadingMaxSizeIndicator()
                        is LoadState.Success -> {

                            SearchContent(
                                query = vm.query,
                                searchType = vm.currentTab,
                                coverage = vm.currentCoverage,
                                onSearchTypeChange = vm::onSearchTypeChange,

                                seriesResults = vm.seriesResults,
                                seriesCurrentPage = vm.seriesCurrentPage,
                                seriesTotalPages = vm.seriesTotalPages,
                                onSeriesPageChange = vm::onSeriesPageChange,
                                onSeriesClick = { navigator.push(seriesScreen(it)) },

                                bookResults = vm.bookResults,
                                bookCurrentPage = vm.bookCurrentPage,
                                bookTotalPages = vm.bookTotalPages,
                                onBookPageChange = vm::onBookPageChange,
                                onBookClick = { navigator.push(bookScreen(it)) },
                                showToolbar = false,
                            )

                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchViewOptions(viewModel: SearchViewModel) {
        val options = buildList {
            if (viewModel.seriesResults.isNotEmpty() || viewModel.currentTab == SearchViewModel.SearchResultsTab.SERIES) {
                add(LabeledEntry(SearchViewModel.SearchResultsTab.SERIES, stringResource(Res.string.search_series_tab)))
            }
            if (viewModel.bookResults.isNotEmpty() || viewModel.currentTab == SearchViewModel.SearchResultsTab.BOOKS) {
                add(LabeledEntry(SearchViewModel.SearchResultsTab.BOOKS, stringResource(Res.string.search_books_tab)))
            }
        }
        BrowseOptionsButton(active = viewModel.currentTab == SearchViewModel.SearchResultsTab.BOOKS) { dismiss ->
            DropdownChoiceMenu(
                selectedOption = options.first { it.value == viewModel.currentTab },
                options = options,
                onOptionChange = { viewModel.onSearchTypeChange(it.value); dismiss() },
                label = { Text(stringResource(Res.string.browse_content_type)) },
            )
        }
    }
}
