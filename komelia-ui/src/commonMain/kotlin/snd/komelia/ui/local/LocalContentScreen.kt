package snd.komelia.ui.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import snd.komelia.ui.LoadState
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.reader.readerScreen

class LocalContentScreen : ReloadableScreen {
    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getLocalContentViewModel() }
        val navigator = LocalNavigator.currentOrThrow
        val reloadEvents = LocalReloadEvents.current

        LaunchedEffect(Unit) {
            vm.initialize()
            reloadEvents.collect { vm.reload() }
        }

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::refreshFromSources) {
            when (val state = vm.state.collectAsState().value) {
                is LoadState.Error -> ErrorContent(state.exception, vm::reload)
                LoadState.Uninitialized -> LoadingMaxSizeIndicator()
                LoadState.Loading, is LoadState.Success -> {
                    val books by vm.books.collectAsState()
                    LocalContent(
                        books = books,
                        query = vm.query.collectAsState().value,
                        onQueryChange = { vm.query.value = it },
                        selectedSource = vm.source.collectAsState().value,
                        onSourceChange = vm::onSourceChange,
                        selectedSort = vm.sort.collectAsState().value,
                        onSortChange = vm::onSortChange,
                        currentPage = vm.currentPage.collectAsState().value,
                        totalPages = vm.totalPages.collectAsState().value,
                        onPageChange = vm::onPageChange,
                        cardWidth = vm.cardWidth.collectAsState().value,
                        bookMenuActions = vm.bookMenuActions(),
                        loading = state == LoadState.Loading,
                        onBookClick = { navigator.push(bookScreen(it)) },
                        onBookReadClick = { book, markProgress ->
                            navigator.parent?.push(readerScreen(book, markProgress))
                        },
                    )
                }
            }
        }
    }
}
