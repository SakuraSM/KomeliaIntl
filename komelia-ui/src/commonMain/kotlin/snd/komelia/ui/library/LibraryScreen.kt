package snd.komelia.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.error_unknown
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_add
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_all_libraries
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_more
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_tab_collections
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_tab_readlists
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.library_tab_series
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_source
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_libraries
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_libraries_unavailable
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.ui.LoadState.Uninitialized
import snd.komelia.ui.LocalKomgaState
import snd.komelia.ui.LocalLibraries
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalOfflineMode
import snd.komelia.ui.LocalPlatform
import snd.komelia.ui.LocalReloadEvents
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.LocalWindowWidth
import snd.komelia.ui.ReloadableScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.AppFilterChipDefaults
import snd.komelia.ui.common.components.ErrorContent
import snd.komelia.ui.common.components.KomeliaTopBarSurface
import snd.komelia.ui.common.components.LoadingMaxSizeIndicator
import snd.komelia.ui.common.components.komeliaTopBarScroll
import snd.komelia.ui.common.components.rememberKomeliaTopBarScrollState
import snd.komelia.ui.common.menus.LibraryActionsMenu
import snd.komelia.ui.common.menus.LibraryMenuActions
import snd.komelia.ui.dialogs.libraryedit.LibraryEditDialogs
import snd.komelia.ui.library.LibraryTab.COLLECTIONS
import snd.komelia.ui.library.LibraryTab.READ_LISTS
import snd.komelia.ui.library.LibraryTab.SERIES
import snd.komelia.ui.library.view.LibraryCollectionsContent
import snd.komelia.ui.library.view.LibraryReadListsContent
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.ScreenPullToRefreshBox
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.series.list.SeriesListContent
import snd.komelia.ui.series.seriesScreen
import snd.komga.client.common.KomgaAuthor
import snd.komga.client.library.KomgaLibrary
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesStatus
import snd.komelia.offline.local.isLocalLibrary
import kotlin.jvm.Transient

class LibraryScreen(
    val libraryId: KomgaLibraryId? = null,
    @Transient
    private val seriesFilter: SeriesScreenFilter? = null,
    @Transient
    private val initialTab: LibraryTab = SERIES,
) : ReloadableScreen {

    override val key: ScreenKey = "${libraryId}_${seriesFilter.hashCode()}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModelFactory = LocalViewModelFactory.current
        val vm = rememberScreenModel(libraryId?.value) { viewModelFactory.getLibraryViewModel(libraryId) }
        val reloadEvents = LocalReloadEvents.current
        val remoteLibraries = LocalLibraries.current.collectAsState().value
        val localLibrariesVm = rememberScreenModel("local-library-scopes") {
            viewModelFactory.getLocalLibraryViewModel()
        }
        LaunchedEffect(Unit) { localLibrariesVm.initialize() }
        val libraryScopes = remember(remoteLibraries, localLibrariesVm.libraries) {
            mergeLibraryScopes(
                remote = remoteLibraries.map { LibraryScopeItem(it.id, it.name, it.unavailable, false) },
                local = localLibrariesVm.libraries.map { LibraryScopeItem(it.id, it.name, it.unavailable, true) },
            )
        }
        val width = LocalWindowWidth.current
        val libraryActions = remember(vm) { vm.libraryActions() }

        LaunchedEffect(libraryId) {
            vm.selectTab(initialTab)
            vm.initialize(seriesFilter)
            reloadEvents.collect { vm.reload() }
        }

        LaunchedEffect(libraryId, libraryScopes) {
            if (libraryId != null && libraryScopes.isNotEmpty() && libraryScopes.none { it.id == libraryId }) {
                navigator.replaceAll(LibraryScreen(initialTab = vm.currentTab))
            }
        }
        DisposableEffect(Unit) {
            vm.startKomgaEventHandler()
            onDispose { vm.stopKomgaEventHandler() }
        }

        ScreenPullToRefreshBox(screenState = vm.state, onRefresh = vm::reload) {
            when (val state = vm.state.collectAsState().value) {
                is Error -> ErrorContent(exception = state.exception, onReload = vm::reload)
                Uninitialized, Loading, is Success -> {
                    val onScopeSelected: (KomgaLibraryId?) -> Unit = { selectedId ->
                        if (selectedId != libraryId) {
                            navigator.replaceAll(
                                LibraryScreen(
                                    libraryId = selectedId,
                                    initialTab = vm.currentTab,
                                )
                            )
                        }
                    }

                    Row {
                        if (width == FULL) {
                            LibrarySupportingPane(
                                libraries = libraryScopes,
                                selectedLibraryId = libraryId,
                                onSelect = onScopeSelected,
                            )
                        }

                        val topBarScrollState = rememberKomeliaTopBarScrollState()
                        Column(
                            Modifier
                                .weight(1f)
                                .komeliaTopBarScroll(topBarScrollState)
                        ) {
                            if (vm.showToolbar.collectAsState().value) {
                                KomeliaTopBarSurface(
                                    isContentScrolled = topBarScrollState.isContentScrolled,
                                ) {
                                    LibraryToolBar(
                                        library = vm.library.collectAsState().value,
                                        libraries = libraryScopes,
                                        showScopeSelector = width != FULL,
                                        selectedLibraryId = libraryId,
                                        onLibrarySelect = onScopeSelected,
                                        currentTab = vm.currentTab,
                                        libraryActions = libraryActions,
                                        collectionsCount = vm.collectionsCount,
                                        readListsCount = vm.readListsCount,
                                        onBrowseClick = vm::toBrowseTab,
                                        onCollectionsClick = vm::toCollectionsTab,
                                        onReadListsClick = vm::toReadListsTab
                                    )
                                }
                            }

                            when (vm.currentTab) {
                                SERIES -> BrowseTab(vm.seriesTabState)
                                COLLECTIONS -> CollectionsTab(vm.collectionsTabState)
                                READ_LISTS -> ReadListsTab(vm.readListsTabState)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun BrowseTab(seriesTabState: LibrarySeriesTabState) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { seriesTabState.initialize(seriesFilter) }
        DisposableEffect(Unit) {
            seriesTabState.startKomgaEventHandler()
            onDispose { seriesTabState.stopKomgaEventHandler() }
        }

        when (val state = seriesTabState.state.collectAsState().value) {
            is Error -> ErrorContent(
                exception = state.exception,
                onReload = seriesTabState::reload
            )

            else -> {
                val loading = state is Loading || state is Uninitialized
                SeriesListContent(
                    series = seriesTabState.series,
                    seriesActions = seriesTabState.seriesMenuActions(),
                    seriesTotalCount = seriesTabState.totalSeriesCount,
                    onSeriesClick = { navigator.push(seriesScreen(it)) },

                    editMode = seriesTabState.isInEditMode.collectAsState().value,
                    onEditModeChange = seriesTabState::onEditModeChange,
                    selectedSeries = seriesTabState.selectedSeries,
                    onSeriesSelect = seriesTabState::onSeriesSelect,

                    isLoading = loading,
                    filterState = seriesTabState.filterState,

                    currentPage = seriesTabState.currentSeriesPage,
                    totalPages = seriesTabState.totalSeriesPages,
                    pageSize = seriesTabState.pageLoadSize.collectAsState().value,
                    onPageSizeChange = seriesTabState::onPageSizeChange,
                    onPageChange = seriesTabState::onPageChange,

                    minSize = seriesTabState.cardWidth.collectAsState().value,
                )
            }
        }
    }

    @Composable
    private fun CollectionsTab(collectionsTabState: LibraryCollectionsTabState) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { collectionsTabState.initialize() }
        DisposableEffect(Unit) {
            collectionsTabState.startKomgaEventHandler()
            onDispose { collectionsTabState.stopKomgaEventHandler() }
        }

        when (val state = collectionsTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> ErrorContent(
                exception = state.exception,
                onReload = collectionsTabState::reload
            )

            else -> {
                val loading = state is Loading
                LibraryCollectionsContent(
                    collections = collectionsTabState.collections,
                    collectionsTotalCount = collectionsTabState.totalCollections,
                    onCollectionClick = { navigator push CollectionScreen(it) },
                    onCollectionDelete = collectionsTabState::onCollectionDelete,
                    isLoading = loading,

                    totalPages = collectionsTabState.totalPages,
                    currentPage = collectionsTabState.currentPage,
                    pageSize = collectionsTabState.pageSize,
                    onPageChange = collectionsTabState::onPageChange,
                    onPageSizeChange = collectionsTabState::onPageSizeChange,

                    minSize = collectionsTabState.cardWidth.collectAsState().value
                )

            }
        }

    }

    @Composable
    private fun ReadListsTab(readListTabState: LibraryReadListsTabState) {
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(libraryId) { readListTabState.initialize() }
        DisposableEffect(Unit) {
            readListTabState.startKomgaEventHandler()
            onDispose { readListTabState.stopKomgaEventHandler() }
        }

        when (val state = readListTabState.state.collectAsState().value) {
            Uninitialized -> LoadingMaxSizeIndicator()
            is Error -> Text(stringResource(Res.string.error_unknown))
            else -> {
                val loading = state is Loading
                LibraryReadListsContent(
                    readLists = readListTabState.readLists,
                    readListsTotalCount = readListTabState.totalReadLists,
                    onReadListClick = { navigator push ReadListScreen(it) },
                    onReadListDelete = readListTabState::onReadListDelete,
                    isLoading = loading,

                    totalPages = readListTabState.totalPages,
                    currentPage = readListTabState.currentPage,
                    pageSize = readListTabState.pageSize,
                    onPageChange = readListTabState::onPageChange,
                    onPageSizeChange = readListTabState::onPageSizeChange,

                    minSize = readListTabState.cardWidth.collectAsState().value
                )
            }
        }
    }

}

@Composable
private fun LibraryToolBar(
    library: KomgaLibrary?,
    libraries: List<LibraryScopeItem>,
    showScopeSelector: Boolean,
    selectedLibraryId: KomgaLibraryId?,
    onLibrarySelect: (KomgaLibraryId?) -> Unit,
    currentTab: LibraryTab,
    libraryActions: LibraryMenuActions,
    collectionsCount: Int,
    readListsCount: Int,
    onBrowseClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onReadListsClick: () -> Unit,
) {

    val layout = LocalKomeliaLayout.current
    val chipColors = AppFilterChipDefaults.filterChipColors()
    var showOptionsMenu by remember { mutableStateOf(false) }
    val isAdmin = LocalKomgaState.current.authenticatedUser.collectAsState().value?.roleAdmin() ?: true
    val isOffline = LocalOfflineMode.current.collectAsState().value
    val platform = LocalPlatform.current

    Column(
        modifier = Modifier.padding(vertical = layout.controlSpacing),
        verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
    ) {
        if (showScopeSelector && platform == MOBILE) {
            MobileLibraryScopeBar(
                libraries = libraries,
                selectedLibraryId = selectedLibraryId,
                onSelect = onLibrarySelect,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = layout.pageHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (platform != MOBILE || library != null) item {
                if (showScopeSelector && platform != MOBILE) {
                    LibraryScopeSelector(
                        libraries = libraries,
                        selectedLibraryId = selectedLibraryId,
                        onSelect = onLibrarySelect,
                    )
                } else if (!showScopeSelector) {
                    Text(library?.name ?: stringResource(Res.string.library_all_libraries))
                }

                if (library != null && !library.id.isLocalLibrary() && (isAdmin || isOffline)) {
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null,
                            )
                        }

                        LibraryActionsMenu(
                            library = library,
                            actions = libraryActions,
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false }
                        )
                    }
                }

                Spacer(Modifier.width(layout.controlSpacing))
            }

            if (collectionsCount > 0 || readListsCount > 0)
                item {
                    FilterChip(
                        onClick = onBrowseClick,
                        selected = currentTab == SERIES,
                        label = { Text(stringResource(Res.string.library_tab_series)) },
                        colors = chipColors,
                        border = null,
                    )
                }

            if (collectionsCount > 0)
                item {
                    FilterChip(
                        onClick = onCollectionsClick,
                        selected = currentTab == COLLECTIONS,
                        label = { Text(stringResource(Res.string.library_tab_collections)) },
                        colors = chipColors,
                        border = null,
                    )
                }

            if (readListsCount > 0)
                item {
                    FilterChip(
                        onClick = onReadListsClick,
                        selected = currentTab == READ_LISTS,
                        label = { Text(stringResource(Res.string.library_tab_readlists)) },
                        colors = chipColors,
                        border = null,
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileLibraryScopeBar(
    libraries: List<LibraryScopeItem>,
    selectedLibraryId: KomgaLibraryId?,
    onSelect: (KomgaLibraryId?) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    val colors = AppFilterChipDefaults.filterChipColors()
    var expanded by remember { mutableStateOf(false) }
    var showLibraryAddDialog by remember { mutableStateOf(false) }
    val selectedLibrary = libraries.firstOrNull { it.id == selectedLibraryId }
    val orderedLibraries = remember(libraries, selectedLibraryId) {
        buildList {
            selectedLibrary?.let(::add)
            addAll(libraries.filterNot { it.id == selectedLibraryId })
        }
    }
    val visibleLibraries = remember(orderedLibraries) { orderedLibraries.take(2) }
    val overflowLibraries = remember(orderedLibraries) { orderedLibraries.drop(2) }

    if (showLibraryAddDialog) {
        LibraryEditDialogs(library = null, onDismissRequest = { showLibraryAddDialog = false })
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = layout.pageHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item("all-libraries") {
            FilterChip(
                selected = selectedLibraryId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(Res.string.library_all_libraries)) },
                colors = colors,
                border = null,
            )
        }
        if (overflowLibraries.isNotEmpty()) {
            item("more-libraries") {
                FilterChip(
                    selected = false,
                    onClick = { expanded = true },
                    leadingIcon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
                    label = { Text(stringResource(Res.string.library_more)) },
                    colors = colors,
                    border = null,
                )
            }
        }
        items(visibleLibraries, key = { it.id.value }) { item ->
            FilterChip(
                selected = item.id == selectedLibraryId,
                onClick = { onSelect(item.id) },
                label = {
                    Text(
                        item.name,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                },
                colors = colors,
                border = null,
            )
        }
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Text(
                stringResource(Res.string.navbar_libraries),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = layout.dialogContentPadding),
            )
            LibraryScopeItems(
                libraries = overflowLibraries,
                selectedLibraryId = selectedLibraryId,
                onSelect = {
                    expanded = false
                    onSelect(it)
                },
                onAddLibrary = {
                    expanded = false
                    showLibraryAddDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = layout.dialogContentPadding)
                    .padding(bottom = layout.dialogContentPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScopeSelector(
    libraries: List<LibraryScopeItem>,
    selectedLibraryId: KomgaLibraryId?,
    onSelect: (KomgaLibraryId?) -> Unit,
) {
    val platform = LocalPlatform.current
    val layout = LocalKomeliaLayout.current
    var expanded by remember { mutableStateOf(false) }
    var showLibraryAddDialog by remember { mutableStateOf(false) }
    val selectedName = libraries.firstOrNull { it.id == selectedLibraryId }?.name
        ?: stringResource(Res.string.library_all_libraries)

    if (showLibraryAddDialog) {
        LibraryEditDialogs(library = null, onDismissRequest = { showLibraryAddDialog = false })
    }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = layout.minimumTouchTarget),
        ) {
            Text(selectedName)
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }

        if (platform == MOBILE) {
            if (expanded) {
                ModalBottomSheet(onDismissRequest = { expanded = false }) {
                    Text(
                        stringResource(Res.string.navbar_libraries),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = layout.dialogContentPadding),
                    )
                    LibraryScopeItems(
                        libraries = libraries,
                        selectedLibraryId = selectedLibraryId,
                        onSelect = {
                            expanded = false
                            onSelect(it)
                        },
                        onAddLibrary = {
                            expanded = false
                            showLibraryAddDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 600.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = layout.dialogContentPadding)
                            .padding(bottom = layout.dialogContentPadding),
                    )
                }
            }
        } else {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(280.dp),
            ) {
                LibraryScopeMenuItems(
                    libraries = libraries,
                    selectedLibraryId = selectedLibraryId,
                    onSelect = {
                        expanded = false
                        onSelect(it)
                    },
                    onAddLibrary = {
                        expanded = false
                        showLibraryAddDialog = true
                    },
                )
            }
        }
    }
}

@Composable
private fun LibrarySupportingPane(
    libraries: List<LibraryScopeItem>,
    selectedLibraryId: KomgaLibraryId?,
    onSelect: (KomgaLibraryId?) -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    var showLibraryAddDialog by remember { mutableStateOf(false) }
    if (showLibraryAddDialog) {
        LibraryEditDialogs(library = null, onDismissRequest = { showLibraryAddDialog = false })
    }

    Surface(
        modifier = Modifier
            .width(232.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            stringResource(Res.string.navbar_libraries),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                start = layout.pageHorizontalPadding,
                end = layout.pageHorizontalPadding,
                top = layout.pageVerticalPadding,
            ),
        )
        LibraryScopeItems(
            libraries = libraries,
            selectedLibraryId = selectedLibraryId,
            onSelect = onSelect,
            onAddLibrary = { showLibraryAddDialog = true },
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(layout.pageHorizontalPadding),
        )
    }
}

@Composable
private fun LibraryScopeItems(
    libraries: List<LibraryScopeItem>,
    selectedLibraryId: KomgaLibraryId?,
    onSelect: (KomgaLibraryId?) -> Unit,
    onAddLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = spacedBy(4.dp),
    ) {
        LibraryScopeRow(
            label = stringResource(Res.string.library_all_libraries),
            selected = selectedLibraryId == null,
            onClick = { onSelect(null) },
        )
        libraries.forEach { library ->
            LibraryScopeRow(
                label = library.name,
                supportingText = when {
                    library.unavailable -> stringResource(Res.string.navbar_libraries_unavailable)
                    library.isLocal -> stringResource(Res.string.local_source)
                    else -> null
                },
                selected = selectedLibraryId == library.id,
                onClick = { onSelect(library.id) },
            )
        }
        LibraryScopeRow(
            label = stringResource(Res.string.library_add),
            leadingIcon = Icons.Default.Add,
            onClick = onAddLibrary,
        )
    }
}

@Composable
private fun LibraryScopeMenuItems(
    libraries: List<LibraryScopeItem>,
    selectedLibraryId: KomgaLibraryId?,
    onSelect: (KomgaLibraryId?) -> Unit,
    onAddLibrary: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.library_all_libraries)) },
        onClick = { onSelect(null) },
        leadingIcon = if (selectedLibraryId == null) {
            { Icon(Icons.Default.Check, contentDescription = null) }
        } else {
            null
        },
    )
    libraries.forEach { library ->
        DropdownMenuItem(
            text = {
                Column {
                    Text(library.name)
                    if (library.unavailable) {
                        Text(
                            stringResource(Res.string.navbar_libraries_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (library.isLocal) {
                        Text(
                            stringResource(Res.string.local_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            onClick = { onSelect(library.id) },
            leadingIcon = if (selectedLibraryId == library.id) {
                { Icon(Icons.Default.Check, contentDescription = null) }
            } else {
                null
            },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.library_add)) },
        onClick = onAddLibrary,
        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
    )
}

internal data class LibraryScopeItem(
    val id: KomgaLibraryId,
    val name: String,
    val unavailable: Boolean,
    val isLocal: Boolean,
)

internal fun mergeLibraryScopes(
    remote: List<LibraryScopeItem>,
    local: List<LibraryScopeItem>,
): List<LibraryScopeItem> {
    val localIds = local.mapTo(mutableSetOf()) { it.id }
    return remote
        .filterNot { it.id in localIds }
        .distinctBy { it.id } + local.distinctBy { it.id }
}

@Composable
private fun LibraryScopeRow(
    label: String,
    selected: Boolean = false,
    supportingText: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val layout = LocalKomeliaLayout.current
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supportingText?.let { text ->
            {
                Text(text, color = MaterialTheme.colorScheme.error)
            }
        },
        leadingContent = when {
            leadingIcon != null -> ({ Icon(leadingIcon, contentDescription = null) })
            selected -> ({ Icon(Icons.Default.Check, contentDescription = null) })
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = layout.minimumTouchTarget)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        tonalElevation = 0.dp,
    )
}


data class SeriesScreenFilter(
    val publicationStatus: List<KomgaSeriesStatus>? = null,
    val ageRating: List<Int>? = null,
    val language: List<String>? = null,
    val publisher: List<String>? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val authors: List<KomgaAuthor>? = null,
)
