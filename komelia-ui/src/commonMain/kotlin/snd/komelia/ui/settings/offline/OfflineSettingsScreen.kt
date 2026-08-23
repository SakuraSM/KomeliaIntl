package snd.komelia.ui.settings.offline

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_downloads_tab
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_cache_tab
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_logs_tab
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_title
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_offline_mode_users_tab
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.MainScreen
import snd.komelia.ui.appRootNavigator
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komelia.ui.settings.offline.cache.OfflineCacheContent
import snd.komelia.ui.settings.offline.downloads.OfflineDownloadsContent
import snd.komelia.ui.settings.offline.logs.OfflineLogsContent
import snd.komelia.ui.settings.offline.users.OfflineUserSettingsContent
import snd.komga.client.book.KomgaBookId
import snd.komga.client.series.KomgaSeriesId

class OfflineSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val currentNavigator = LocalNavigator.currentOrThrow
        val rootNavigator = currentNavigator.appRootNavigator()
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel { viewModelFactory.getOfflineModeSettingsViewModel() }

        LaunchedEffect(Unit) {
            vm.initialize(rootNavigator)
        }

        SettingsScreenContainer(stringResource(Res.string.settings_offline_mode_title)) {
            var selectedTab by rememberSaveable { mutableStateOf(0) }

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_offline_mode_users_tab))
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_offline_mode_downloads_tab))
                }

                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_offline_mode_cache_tab))
                }

                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    modifier = Modifier.heightIn(min = 40.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_offline_mode_logs_tab))
                }
            }
            when (selectedTab) {
                0 -> {
                    val userState = vm.usersState
                    OfflineUserSettingsContent(
                        currentUser = userState.currentUser.collectAsState().value,
                        onlineServerUrl = userState.onlineServerUrl.collectAsState().value,
                        isOffline = userState.isOffline.collectAsState(false).value,
                        goOnline = userState::goOnline,
                        loginAs = userState::loginAs,
                        serverUsers = userState.offlineUsers.collectAsState().value,
                        onServerDelete = userState::onServerDelete,
                        onUserDelete = userState::onUserDelete,
                    )
                }

                1 -> {
                    val downloadsState = vm.downloadsSate
                    OfflineDownloadsContent(
                        storageLocation = downloadsState.storageLocation.collectAsState().value,
                        onStorageLocationChange = downloadsState::onStorageLocationChange,
                        onStorageLocationReset = downloadsState::onStorageLocationReset,
                        downloads = downloadsState.downloads.collectAsState().value,
                        onDownloadCancel = downloadsState::onDownloadCancel
                    )
                }

                2 -> {
                    val state = vm.cacheState
                    OfflineCacheContent(
                        catalog = state.catalog.collectAsState().value,
                        loadState = state.loadState.collectAsState().value,
                        selectedMediaKind = state.selectedMediaKind.collectAsState().value,
                        onMediaKindSelect = state::selectMediaKind,
                        onRetry = state::retry,
                        onOpenBook = { id ->
                            rootNavigator.replaceAll(
                                MainScreen(BookScreen(KomgaBookId(id), BookSiblingsContext.Series))
                            )
                        },
                        onOpenSeries = { id ->
                            rootNavigator.replaceAll(MainScreen(SeriesScreen(KomgaSeriesId(id))))
                        },
                        onDeleteBook = state::deleteBook,
                        onDeleteSeries = state::deleteSeries,
                        onDeleteAll = state::deleteAll,
                    )
                }

                3 -> {
                    val state = vm.logsState
                    OfflineLogsContent(
                        logs = state.logs.collectAsState().value,
                        loadState = state.loadState.collectAsState().value,
                        totalPages = state.totalPages.collectAsState().value,
                        currentPage = state.pageNumber.collectAsState().value,
                        onPageChange = state::onPageChange,
                        selectedTab = state.tab.collectAsState().value,
                        onTabSelect = state::onTabChange,
                        onDelete = state::onLogsDelete,
                        onRetry = state::retry,
                    )
                }
            }


        }
    }
}
