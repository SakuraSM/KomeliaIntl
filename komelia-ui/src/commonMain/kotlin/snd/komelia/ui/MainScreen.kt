package snd.komelia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue.Closed
import androidx.compose.material3.DrawerValue.Open
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.home.HomeScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType.DESKTOP
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.platform.PlatformType.WEB_KOMF
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.platform.WindowSizeClass.FULL
import snd.komelia.ui.search.SearchScreen
import snd.komelia.ui.series.seriesScreen
import snd.komelia.ui.settings.MobileSettingsScreen
import snd.komelia.ui.settings.SettingsScreen
import snd.komelia.ui.topbar.AppBar
import snd.komelia.ui.topbar.LibrariesNavBarContent
import snd.komelia.ui.topbar.NavBarContent

class MainScreen(
    private val defaultScreen: Screen = HomeScreen()
) : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val platform = LocalPlatform.current

        Navigator(
            screen = defaultScreen,
            onBackPressed = null,
        ) { navigator ->

            val vm = rememberScreenModel { viewModelFactory.getNavigationViewModel() }
            when (platform) {
                MOBILE -> MobileLayout(navigator, vm)
                DESKTOP, WEB_KOMF -> DesktopLayout(navigator, vm)
            }
            LaunchedEffect(Unit) {
                vm.initialize(navigator)
            }

            val keyEvents: SharedFlow<KeyEvent> = LocalKeyEvents.current
            LaunchedEffect(Unit) {
                keyEvents.collect { event ->
                    if (event.type == KeyUp && event.key == Key.DirectionLeft && event.isAltPressed) {
                        navigator.pop()
                    }

                }
            }
        }
    }

    @Composable
    private fun DesktopLayout(
        navigator: Navigator,
        vm: MainScreenViewModel
    ) {
        val width = LocalWindowWidth.current
        LaunchedEffect(width) {
            when (width) {
                FULL -> vm.navBarState.snapTo(Open)
                else -> vm.navBarState.snapTo(Closed)
            }
        }
        Column {
            AppBar(
                onMenuButtonPress = { vm.toggleNavBar() },
                query = vm.searchBarState.currentQuery(),
                onQueryChange = vm.searchBarState::onQueryChange,
                isLoading = vm.searchBarState.isLoading,
                onSearchAllClick = {
                    if (navigator.lastItem is SearchScreen) navigator.replace(SearchScreen(it))
                    else navigator.push(SearchScreen(it))
                },
                searchResults = vm.searchBarState.searchResults(),
                libraryById = vm.searchBarState::getLibraryById,
                onBookClick = { navigator.replaceAll(bookScreen(it)) },
                onSeriesClick = {
                    navigator.replaceAll(seriesScreen(it))
                },
                onRefreshClick = vm::onScreenReload,
                notificationsState = vm.notificationsState,
                isOffline = vm.isOffline.collectAsState().value,
                onOfflineModeChange = vm::goOnline
            )

            when (width) {
                FULL -> Row {
                    if (vm.navBarState.targetValue == Open) NavBar(vm, navigator, width)
                    CurrentScreen()
                }

                else -> ModalNavigationDrawer(
                    drawerState = vm.navBarState,
                    drawerContent = { NavBar(vm, navigator, width) },
                    content = { CurrentScreen() }
                )
            }
        }
    }

    @Composable
    private fun MobileLayout(
        navigator: Navigator,
        vm: MainScreenViewModel
    ) {
        val coroutineScope = rememberCoroutineScope()
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                BottomNavigationBar(
                    navigator = navigator,
                    toggleLibrariesDrawer = { coroutineScope.launch { vm.toggleNavBar() } },
                    navigateFromBottom = { navigate ->
                        coroutineScope.launch {
                            if (vm.navBarState.isOpen) vm.navBarState.snapTo(Closed)
                            navigate()
                        }
                    },
                    modifier = Modifier
                )
            },
        ) { paddingValues ->
            val layoutDirection = LocalLayoutDirection.current

            ModalNavigationDrawer(
                drawerState = vm.navBarState,
                drawerContent = {
                    LibrariesNavBar(
                        modifier = Modifier.padding(
                            start = paddingValues.calculateStartPadding(layoutDirection),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding(),
                        ).consumeWindowInsets(paddingValues),
                        vm = vm,
                        navigator = navigator
                    )
                },
                content = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = paddingValues.calculateStartPadding(layoutDirection),
                                end = paddingValues.calculateEndPadding(layoutDirection),
                                top = paddingValues.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding(),
                            )
                            .consumeWindowInsets(paddingValues)
                    ) {
                        CurrentScreen()
                    }
                }
            )

            if (vm.navBarState.isOpen || navigator.canPop) {
                BackPressHandler {
                    if (vm.navBarState.isOpen) {
                        coroutineScope.launch { vm.navBarState.close() }
                    } else if (navigator.canPop) {
                        navigator.pop()
                    }
                }
            }
        }
    }

    @Composable
    private fun BottomNavigationBar(
        navigator: Navigator,
        toggleLibrariesDrawer: () -> Unit,
        navigateFromBottom: (() -> Unit) -> Unit,
        modifier: Modifier
    ) {
        val strings = LocalStrings.current.mainNavigation
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    modifier = modifier,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    CompactNavButton(
                        text = strings.libraries,
                        icon = Icons.Rounded.LocalLibrary,
                        onClick = { toggleLibrariesDrawer() },
                        isSelected = false,
                        modifier = Modifier.weight(1f),
                    )

                    CompactNavButton(
                        text = strings.home,
                        icon = Icons.Rounded.Home,
                        onClick = { navigateFromBottom { navigator.replaceAll(HomeScreen()) } },
                        isSelected = navigator.lastItem is HomeScreen,
                        modifier = Modifier.weight(1f),
                    )


                    CompactNavButton(
                        text = strings.search,
                        icon = Icons.Rounded.Search,
                        onClick = { navigateFromBottom { navigator.push(SearchScreen(null)) } },
                        isSelected = navigator.lastItem is SearchScreen,
                        modifier = Modifier.weight(1f),
                    )

                    CompactNavButton(
                        text = strings.settings,
                        icon = Icons.Rounded.Settings,
                        onClick = { navigateFromBottom { navigator.parent!!.push(MobileSettingsScreen()) } },
                        isSelected = navigator.lastItem is SettingsScreen,
                        modifier = Modifier.weight(1f),
                    )

                }
            }
        }
    }

    @Composable
    private fun RowScope.CompactNavButton(
        text: String,
        icon: ImageVector,
        onClick: () -> Unit,
        isSelected: Boolean,
        modifier: Modifier
    ) {
        val layout = LocalKomeliaLayout.current
        NavigationBarItem(
            modifier = modifier.heightIn(min = layout.minimumTouchTarget),
            selected = isSelected,
            onClick = onClick,
            icon = { androidx.compose.material3.Icon(icon, contentDescription = text) },
            label = { Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            alwaysShowLabel = true,
        )
    }


    @Composable
    private fun NavBar(
        vm: MainScreenViewModel,
        navigator: Navigator,
        width: WindowSizeClass
    ) {
        val coroutineScope = rememberCoroutineScope()
        NavBarContent(
            currentScreen = navigator.lastItem,
            libraries = vm.libraries.collectAsState().value,
            libraryActions = vm.getLibraryActions(),
            onHomeClick = {
                navigator.replaceAll(HomeScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onLibrariesClick = {
                navigator.replaceAll(LibraryScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },

            onLibraryClick = {
                navigator.replaceAll(LibraryScreen(it))
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onSettingsClick = {
                navigator.parent!!.push(SettingsScreen())
                if (width != FULL) coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onLibrariesRefreshClick = vm::refreshLibraries,
            taskQueueStatus = vm.komgaTaskQueueStatus.collectAsState().value
        )
    }

    @Composable
    private fun LibrariesNavBar(
        modifier: Modifier,
        vm: MainScreenViewModel,
        navigator: Navigator,
    ) {
        val coroutineScope = rememberCoroutineScope()
        LibrariesNavBarContent(
            modifier = modifier,
            currentScreen = navigator.lastItem,
            libraries = vm.libraries.collectAsState().value,
            libraryActions = vm.getLibraryActions(),
            onLibrariesClick = {
                navigator.replaceAll(LibraryScreen())
                coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },

            onLibraryClick = {
                navigator.replaceAll(LibraryScreen(it))
                coroutineScope.launch { vm.navBarState.snapTo(Closed) }
            },
            onLibrariesRefreshClick = vm::refreshLibraries,
        )
    }
}
