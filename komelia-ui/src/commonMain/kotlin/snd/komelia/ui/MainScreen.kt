package snd.komelia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabDisposable
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_home
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_libraries
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_settings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.komeliaTopBarScroll
import snd.komelia.ui.common.components.rememberKomeliaTopBarScrollState
import snd.komelia.ui.home.HomeScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.platform.BackPressHandler
import snd.komelia.ui.platform.PlatformType.MOBILE
import snd.komelia.ui.readlist.ReadListScreen
import snd.komelia.ui.search.SearchScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.series.seriesScreen
import snd.komelia.ui.settings.MobileSettingsScreen
import snd.komelia.ui.settings.SettingsScreen
import snd.komelia.ui.topbar.AppBar

class MainScreen(
    private val defaultScreen: Screen = HomeScreen(),
) : Screen {

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val platform = LocalPlatform.current
        val width = LocalWindowWidth.current
        val motion = LocalKomeliaMotion.current
        val vm = rememberScreenModel { viewModelFactory.getNavigationViewModel() }
        val tabs = remember(defaultScreen, platform) { createTabs(defaultScreen, platform == MOBILE) }
        val initialTab = tabs.first { it.destination == destinationFor(defaultScreen) }

        TabNavigator(
            tab = initialTab,
            disposeNestedNavigators = false,
            tabDisposable = { TabDisposable(it, tabs) },
            key = "main-destinations",
        ) { tabNavigator ->
            val rootNavigator = LocalNavigator.currentOrThrow
            val rootScreen = rootNavigator.lastItem
            if (rootScreen !is AppTab) {
                rootNavigator.saveableState(immersiveScreenSaveableStateKey(rootScreen.key), rootScreen) {
                    rootScreen.Content()
                }
                return@TabNavigator
            }

            var activeNavigator by remember { mutableStateOf<Navigator?>(null) }
            var activeDestination by remember { mutableStateOf<AppDestination?>(null) }
            var pendingScreen by remember { mutableStateOf<Pair<AppDestination, Screen>?>(null) }
            val topBarScrollState = rememberKomeliaTopBarScrollState()
            // TabNavigator.current is the reactive source of truth. LocalNavigator.lastItem
            // can briefly lag behind a tab selection and caused destination taps to be
            // interpreted as reselections, most visibly when returning to Home.
            val currentTab = tabNavigator.current as AppTab
            fun selectDestination(destination: AppDestination, screen: Screen? = null) {
                val targetTab = tabs.first { it.destination == destination }
                val selectedDestination = (tabNavigator.current as AppTab).destination
                if (screen != null) pendingScreen = destination to screen

                if (
                    destinationSelection(selectedDestination, destination) == DestinationSelection.ReselectCurrent &&
                    screen == null
                ) {
                    if (activeDestination == destination) {
                        activeNavigator?.replaceAll(targetTab.rootScreen)
                    }
                } else {
                    tabNavigator.current = targetTab
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.komeliaTopBarScroll(topBarScrollState)) {
                    if (platform != MOBILE) {
                        AppBar(
                            canNavigateBack = activeNavigator?.canPop == true,
                            onNavigateBack = { activeNavigator?.pop() },
                            currentScreen = activeNavigator?.lastItem,
                            query = vm.searchBarState.currentQuery(),
                            onQueryChange = vm.searchBarState::onQueryChange,
                            isLoading = vm.searchBarState.isLoading,
                            onSearchAllClick = { selectDestination(AppDestination.SEARCH, SearchScreen(it)) },
                            searchResults = vm.searchBarState.searchResults(),
                            libraryById = vm.searchBarState::getLibraryById,
                            onBookClick = { selectDestination(AppDestination.LIBRARY, bookScreen(it)) },
                            onSeriesClick = { selectDestination(AppDestination.LIBRARY, seriesScreen(it)) },
                            onRefreshClick = vm::onScreenReload,
                            notificationsState = vm.notificationsState,
                            isOffline = vm.isOffline.collectAsState().value,
                            onOfflineModeChange = vm::goOnline,
                            isContentScrolled = topBarScrollState.isContentScrolled,
                        )
                    }

                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentWindowInsets = if (platform == MOBILE) {
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        } else {
                            WindowInsets(0.dp)
                        },
                        bottomBar = {
                            AnimatedVisibility(
                                visible = navigationPresentation(width) == NavigationPresentation.BottomBar,
                                enter = if (motion.isReducedMotion) EnterTransition.None else fadeIn(
                                    tween(motion.duration(motion.containerDurationMillis), easing = motion.standardEasing),
                                ),
                                exit = if (motion.isReducedMotion) ExitTransition.None else fadeOut(
                                    tween(motion.duration(motion.containerDurationMillis), easing = motion.standardEasing),
                                ),
                            ) {
                                AppBottomBar(currentTab.destination, ::selectDestination)
                            }
                        },
                    ) { contentPadding ->
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(contentPadding),
                        ) {
                            AnimatedVisibility(
                                visible = navigationPresentation(width) == NavigationPresentation.Rail,
                                enter = if (motion.isReducedMotion) EnterTransition.None else fadeIn(
                                    tween(motion.duration(motion.containerDurationMillis), easing = motion.standardEasing),
                                ),
                                exit = if (motion.isReducedMotion) ExitTransition.None else fadeOut(
                                    tween(motion.duration(motion.containerDurationMillis), easing = motion.standardEasing),
                                ),
                            ) {
                                AppNavigationRail(currentTab.destination, ::selectDestination)
                            }

                            SingleLayerDestinationTransition(
                                targetTab = currentTab,
                                modifier = Modifier.weight(1f),
                            ) { tab ->
                                tabNavigator.saveableState("destination", tab) {
                                    Navigator(
                                        screens = tab.initialScreens,
                                        onBackPressed = null,
                                        key = "destination-${tab.destination.name.lowercase()}",
                                    ) { navigator ->
                                        SideEffect {
                                            if (tabNavigator.current == tab) {
                                                activeNavigator = navigator
                                                activeDestination = tab.destination
                                                vm.initialize(navigator)
                                            }
                                        }
                                        LaunchedEffect(tab, pendingScreen) {
                                            val pending = pendingScreen
                                            if (tabNavigator.current == tab && pending?.first == tab.destination) {
                                                navigator.replaceAll(pending.second)
                                                pendingScreen = null
                                            }
                                        }
                                        DestinationContent(navigator)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val backAction = appBackAction(activeNavigator?.canPop == true, currentTab.destination)
            if (backAction != AppBackAction.ExitApp) {
                BackPressHandler {
                    when (backAction) {
                        AppBackAction.PopDetail -> activeNavigator?.pop()
                        AppBackAction.ReturnHome -> selectDestination(AppDestination.HOME)
                        AppBackAction.ExitApp -> Unit
                    }
                }
            }

            val keyEvents: SharedFlow<KeyEvent> = LocalKeyEvents.current
            LaunchedEffect(tabNavigator, activeNavigator) {
                keyEvents.collect { event ->
                    if (event.type == KeyUp && event.key == Key.DirectionLeft && event.isAltPressed) {
                        if (activeNavigator?.canPop == true) activeNavigator?.pop()
                        else if (currentTab.destination != AppDestination.HOME) selectDestination(AppDestination.HOME)
                    }
                }
            }
        }
    }
}

private class AppTab(
    val destination: AppDestination,
    val rootScreen: Screen,
    val initialScreens: List<Screen>,
) : Tab {
    override val key: String = "app-tab-${destination.name.lowercase()}"
    override val options: TabOptions
        @Composable get() = TabOptions(destination.ordinal.toUShort(), destination.name)

    @Composable
    override fun Content() = Unit
}

private fun createTabs(defaultScreen: Screen, isMobile: Boolean): List<AppTab> {
    val initialDestination = destinationFor(defaultScreen)
    fun tab(destination: AppDestination, rootScreen: Screen): AppTab {
        val initialScreens = when {
            initialDestination != destination -> listOf(rootScreen)
            defaultScreen is LibraryScreen && defaultScreen.libraryId != null -> listOf(rootScreen, defaultScreen)
            defaultScreen::class != rootScreen::class -> listOf(rootScreen, defaultScreen)
            else -> listOf(defaultScreen)
        }
        return AppTab(destination, rootScreen, initialScreens)
    }

    return listOf(
        tab(AppDestination.LIBRARY, LibraryScreen()),
        tab(AppDestination.HOME, HomeScreen()),
        tab(AppDestination.SEARCH, SearchScreen(null)),
        tab(
            AppDestination.SETTINGS,
            if (isMobile) MobileSettingsScreen(topLevel = true) else SettingsScreen(topLevel = true),
        ),
    )
}

private fun destinationFor(screen: Screen): AppDestination = when (screen) {
    is LibraryScreen, is BookScreen, is SeriesScreen, is OneshotScreen, is CollectionScreen, is ReadListScreen ->
        AppDestination.LIBRARY

    is SearchScreen -> AppDestination.SEARCH
    is MobileSettingsScreen, is SettingsScreen -> AppDestination.SETTINGS
    else -> AppDestination.HOME
}

@Composable
private fun DestinationContent(navigator: Navigator) {
    val motion = LocalKomeliaMotion.current
    val detailOffset = with(LocalDensity.current) { 8.dp.roundToPx() }
    val targetScreen = navigator.lastItem
    var displayedScreen by remember { mutableStateOf(targetScreen) }
    var displayedScreenKey by remember { mutableStateOf<String?>(null) }
    val alpha = remember { Animatable(1f) }
    val offset = remember { Animatable(0f) }

    LaunchedEffect(targetScreen, motion.isReducedMotion) {
        val transitionAction = screenTransitionAction(displayedScreenKey, targetScreen.key)
        if (motion.isReducedMotion || transitionAction != ScreenTransitionAction.AnimateChange) {
            displayedScreen = targetScreen
            displayedScreenKey = targetScreen.key
            alpha.snapTo(1f)
            offset.snapTo(0f)
            return@LaunchedEffect
        }

        val totalDuration = motion.duration(motion.contentDurationMillis)
        val exitDuration = (totalDuration * 0.4f).toInt()
        coroutineScope {
            launch { alpha.animateTo(0f, tween(exitDuration, easing = motion.standardEasing)) }
            launch { offset.animateTo(-detailOffset.toFloat(), tween(exitDuration, easing = motion.standardEasing)) }
        }
        displayedScreen = targetScreen
        displayedScreenKey = targetScreen.key
        offset.snapTo(detailOffset.toFloat())
        val enterDuration = totalDuration - exitDuration
        coroutineScope {
            launch { alpha.animateTo(1f, tween(enterDuration, easing = motion.standardEasing)) }
            launch { offset.animateTo(0f, tween(enterDuration, easing = motion.standardEasing)) }
        }
    }

    val screen = displayedScreen
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha.value
                translationX = offset.value
            },
    ) {
        navigator.saveableState("screen", screen) { screen.Content() }
    }
}

@Composable
private fun SingleLayerDestinationTransition(
    targetTab: AppTab,
    modifier: Modifier = Modifier,
    content: @Composable (AppTab) -> Unit,
) {
    val motion = LocalKomeliaMotion.current
    var displayedTab by remember { mutableStateOf(targetTab) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(targetTab, motion.isReducedMotion) {
        if (motion.isReducedMotion) {
            displayedTab = targetTab
            alpha.snapTo(1f)
            return@LaunchedEffect
        }

        val totalDuration = motion.duration(motion.contentDurationMillis)
        val exitDuration = (totalDuration * 0.35f).toInt()
        alpha.animateTo(0f, tween(exitDuration, easing = motion.standardEasing))
        displayedTab = targetTab
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(totalDuration - exitDuration, easing = motion.standardEasing))
    }

    androidx.compose.foundation.layout.Box(
        modifier.graphicsLayer { this.alpha = alpha.value },
    ) {
        content(displayedTab)
    }
}

private data class DestinationItem(
    val destination: AppDestination,
    val label: StringResource,
    val icon: ImageVector,
)

private val destinationItems = listOf(
    DestinationItem(AppDestination.LIBRARY, Res.string.navbar_libraries, Icons.Default.LocalLibrary),
    DestinationItem(AppDestination.HOME, Res.string.navbar_home, Icons.Default.Home),
    DestinationItem(AppDestination.SEARCH, Res.string.navbar_search, Icons.Default.Search),
    DestinationItem(AppDestination.SETTINGS, Res.string.navbar_settings, Icons.Default.Settings),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppBottomBar(
    selected: AppDestination,
    onSelect: (AppDestination, Screen?) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars,
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            destinationItems.forEach { item ->
                NavigationBarItem(
                    selected = selected == item.destination,
                    onClick = { onSelect(item.destination, null) },
                    icon = { DestinationIcon(item) },
                    label = { Text(stringResource(item.label)) },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppNavigationRail(
    selected: AppDestination,
    onSelect: (AppDestination, Screen?) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets.navigationBars,
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            destinationItems.forEach { item ->
                NavigationRailItem(
                    selected = selected == item.destination,
                    onClick = { onSelect(item.destination, null) },
                    icon = { DestinationIcon(item) },
                    label = { Text(stringResource(item.label)) },
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(item: DestinationItem) {
    Icon(item.icon, contentDescription = stringResource(item.label))
}
