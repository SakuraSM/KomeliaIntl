package snd.komelia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import org.jetbrains.compose.resources.painterResource
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.komelia_icon
import snd.komelia.ui.platform.WindowSizeClass
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType.Companion.KeyUp
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_local
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_search
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.navbar_settings
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.book.bookScreen
import snd.komelia.ui.collection.CollectionScreen
import snd.komelia.ui.common.components.GlassBackdropScope
import snd.komelia.ui.common.components.KomeliaChromeStyle
import snd.komelia.ui.common.components.komeliaHighlight
import snd.komelia.ui.common.components.KomeliaChromeSurface
import snd.komelia.ui.common.components.NavigationDestinationLabel
import snd.komelia.ui.common.components.LocalNavigationBarInset
import snd.komelia.ui.common.components.LocalPageTopInset
import snd.komelia.ui.common.components.LocalSurfaceAppearance
import snd.komelia.ui.common.components.glassBackdrop
import snd.komelia.ui.common.components.komeliaTopBarScroll
import snd.komelia.ui.common.components.rememberKomeliaTopBarScrollState
import snd.komelia.ui.home.HomeScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.local.LocalContentScreen
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

internal const val DISPOSE_DESTINATIONS_WHEN_HIDDEN = false

class MainScreen(
    private val defaultScreen: Screen = HomeScreen(),
) : Screen {

    // Persist across Activity recreation, but never reuse the previous login's
    // saveable navigation state when a new MainScreen is created.
    private var navigationId: String? = newNavigationId()
    override val key: String
        get() = navigationId ?: newNavigationId().also { navigationId = it }

    private companion object {
        private const val serialVersionUID = 8985746227260504224L

        @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
        fun newNavigationId(): String = "main-${kotlin.uuid.Uuid.random()}"
    }

    @Composable
    override fun Content() {
        val viewModelFactory = LocalViewModelFactory.current
        val platform = LocalPlatform.current
        val width = LocalWindowWidth.current
        val motion = LocalKomeliaMotion.current
        val vm = rememberScreenModel { viewModelFactory.getNavigationViewModel() }
        val sessionNavigators = rememberScreenModel("session-navigators") { SessionNavigators() }
        val tabs = remember(defaultScreen, platform) { createTabs(defaultScreen, platform == MOBILE) }
        val initialTab = tabs.first { it.destination == destinationFor(defaultScreen) }

        TabNavigator(
            tab = initialTab,
            disposeNestedNavigators = DISPOSE_DESTINATIONS_WHEN_HIDDEN,
            tabDisposable = { TabDisposable(it, tabs) },
            key = "$key-destinations",
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
            val currentPage = if (activeDestination == currentTab.destination) {
                activeNavigator?.lastItem ?: currentTab.initialScreens.last()
            } else currentTab.initialScreens.last()
            val showBottomNavigation = navigationPresentation(width) == NavigationPresentation.BottomBar &&
                currentPage !is BookScreen && currentPage !is OneshotScreen && currentPage !is SeriesScreen
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

            val appNavigationController = remember(tabNavigator, tabs) {
                AppNavigationController { screen ->
                    selectDestination(destinationFor(screen), screen)
                }
            }

            val safeDrawing = WindowInsets.safeDrawing
            val pageTopInset = if (platform == MOBILE) with(LocalDensity.current) { safeDrawing.getTop(this).toDp() } else 0.dp
            GlassBackdropScope {
                CompositionLocalProvider(
                    LocalAppNavigationController provides appNavigationController,
                    LocalPageTopInset provides pageTopInset,
                ) {
                    Surface(
                        color = if (platform == MOBILE) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface,
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
                                containerColor = if (platform == MOBILE) Color.Transparent else MaterialTheme.colorScheme.surface,
                                contentWindowInsets = if (platform == MOBILE) {
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                                } else {
                                    WindowInsets(0.dp)
                                },
                                bottomBar = {
                                    AnimatedVisibility(
                                        visible = showBottomNavigation,
                                        enter = if (motion.isReducedMotion) EnterTransition.None else fadeIn(
                                            tween(
                                                motion.duration(motion.containerDurationMillis),
                                                easing = motion.standardEasing,
                                            ),
                                        ),
                                        exit = if (motion.isReducedMotion || !showBottomNavigation) ExitTransition.None else fadeOut(
                                            tween(
                                                motion.duration(motion.containerDurationMillis),
                                                easing = motion.standardEasing,
                                            ),
                                        ),
                                    ) {
                                        AppBottomBar(currentTab.destination, ::selectDestination)
                                    }
                                },
                            ) { contentPadding ->
                                val floatingBar = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS &&
                                    showBottomNavigation
                                val bottomInset = if (floatingBar) contentPadding.calculateBottomPadding() else 0.dp
                                val direction = LocalLayoutDirection.current
                                val layout = LocalKomeliaLayout.current
                                Row(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(PaddingValues(
                                            start = contentPadding.calculateLeftPadding(direction),
                                            end = contentPadding.calculateRightPadding(direction),
                                            bottom = if (floatingBar) 0.dp else contentPadding.calculateBottomPadding(),
                                        )),
                                ) {
                                    AnimatedVisibility(
                                        visible = navigationPresentation(width) == NavigationPresentation.Rail,
                                        enter = if (motion.isReducedMotion) EnterTransition.None else fadeIn(
                                            tween(
                                                motion.duration(motion.containerDurationMillis),
                                                easing = motion.standardEasing,
                                            ),
                                        ),
                                        exit = if (motion.isReducedMotion) ExitTransition.None else fadeOut(
                                            tween(
                                                motion.duration(motion.containerDurationMillis),
                                                easing = motion.standardEasing,
                                            ),
                                        ),
                                    ) {
                                        AppNavigationRail(currentTab.destination, ::selectDestination)
                                    }

                                    CompositionLocalProvider(
                                        LocalNavigationBarInset provides bottomInset,
                                        LocalKomeliaLayout provides layout.copy(gridBottomPadding = layout.gridBottomPadding + bottomInset),
                                    ) {
                                        SingleLayerDestinationTransition(
                                            targetTab = currentTab,
                                            modifier = Modifier.weight(1f).glassBackdrop(),
                                        ) { tab ->
                                            tabNavigator.saveableState("destination", tab) {
                                                Navigator(
                                                    screens = tab.initialScreens,
                                                    onBackPressed = null,
                                                    key = "$key-destination-${tab.destination.name.lowercase()}",
                                                ) { navigator ->
                                                    SideEffect {
                                                        sessionNavigators.register(tab.destination, navigator)
                                                        if (tabNavigator.current == tab) {
                                                            activeNavigator = navigator
                                                            activeDestination = tab.destination
                                                            vm.initialize(navigator)
                                                        }
                                                    }
                                                    LaunchedEffect(tab, pendingScreen) {
                                                        val pending = pendingScreen
                                                        if (tabNavigator.current == tab && pending?.first == tab.destination) {
                                                            navigator.replaceAll(destinationStack(tab.rootScreen, pending.second))
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

internal class AppTab(
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
        val initialScreens = if (initialDestination == destination) {
            destinationStack(rootScreen, defaultScreen)
        } else {
            listOf(rootScreen)
        }
        return AppTab(destination, rootScreen, initialScreens)
    }

    return listOf(
        tab(AppDestination.LIBRARY, LibraryScreen()),
        tab(AppDestination.HOME, HomeScreen()),
        tab(AppDestination.LOCAL, LocalContentScreen()),
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

    is LocalContentScreen -> AppDestination.LOCAL
    is SearchScreen -> AppDestination.SEARCH
    is MobileSettingsScreen, is SettingsScreen -> AppDestination.SETTINGS
    else -> AppDestination.HOME
}

@Composable
internal fun DestinationContent(navigator: Navigator) {
    val screen = navigator.lastItem
    SingleLayerPageTransition(targetKey = screen.key, enterOffset = 8.dp) {
        navigator.saveableState("screen", screen) { screen.Content() }
    }
}

@Composable
internal fun SingleLayerDestinationTransition(
    targetTab: AppTab,
    modifier: Modifier = Modifier,
    content: @Composable (AppTab) -> Unit,
) {
    SingleLayerPageTransition(targetKey = targetTab.key, modifier = modifier) {
        content(targetTab)
    }
}

private data class DestinationItem(
    val destination: AppDestination,
    val label: StringResource,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

private val destinationItems = listOf(
    DestinationItem(AppDestination.HOME, Res.string.navbar_home, Icons.Rounded.Home, Icons.Rounded.Home),
    DestinationItem(AppDestination.LIBRARY, Res.string.navbar_libraries, Icons.AutoMirrored.Rounded.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    DestinationItem(AppDestination.LOCAL, Res.string.navbar_local, Icons.Rounded.Folder, Icons.Rounded.Folder),
    DestinationItem(AppDestination.SEARCH, Res.string.navbar_search, Icons.Rounded.Search),
    DestinationItem(AppDestination.SETTINGS, Res.string.navbar_settings, Icons.Rounded.Settings, Icons.Rounded.Settings),
).sortedBy { primaryDestinationOrder.indexOf(it.destination) }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppBottomBar(
    selected: AppDestination,
    onSelect: (AppDestination, Screen?) -> Unit,
) {
    val floating = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    val glass = LocalSurfaceAppearance.current.useGlass
    val colors = MaterialTheme.colorScheme
    KomeliaChromeSurface(
        modifier = if (floating) Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp) else Modifier,
        shape = RoundedCornerShape(if (floating) 20.dp else 0.dp),
        shadowElevation = if (floating) 2.dp else 0.dp,
    ) {
        NavigationBar(
            modifier = Modifier.height(68.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = if (floating) WindowInsets(0.dp) else WindowInsets.navigationBars,
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                destinationItems.forEach { item ->
                    val interactions = remember(item.destination) { MutableInteractionSource() }
                    NavigationBarItem(
                        modifier = Modifier.background(
                            if (selected == item.destination) colors.primaryContainer.copy(alpha = .9f) else Color.Transparent,
                            KomeliaChromeStyle.navigationHighlightShape,
                        ).komeliaHighlight(
                            selected == item.destination, KomeliaChromeStyle.navigationHighlightShape,
                            inset = KomeliaChromeStyle.navigationHighlightInset,
                            interactionSource = interactions,
                        ),
                        interactionSource = interactions,
                        selected = selected == item.destination,
                        onClick = { onSelect(item.destination, null) },
                        icon = { DestinationIcon(item, selected == item.destination) },
                        label = { NavigationDestinationLabel(stringResource(item.label), selected == item.destination) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = if (glass) colors.onSurface else colors.onSurfaceVariant,
                            unselectedTextColor = if (glass) colors.onSurface else colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }

}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppNavigationRail(
    selected: AppDestination,
    onSelect: (AppDestination, Screen?) -> Unit,
) {
    val floating = LocalSurfaceAppearance.current.style == AppSurfaceStyle.LIQUID_GLASS
    val glass = LocalSurfaceAppearance.current.useGlass
    val colors = MaterialTheme.colorScheme
    val navigationWidth = if (LocalWindowWidth.current == WindowSizeClass.FULL) 180.dp else 80.dp
    KomeliaChromeSurface(
        modifier = Modifier.width(navigationWidth)
            .then(if (floating) Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp) else Modifier)
            .padding(top = LocalPageTopInset.current),
        shape = RoundedCornerShape(if (floating) 20.dp else 0.dp),
    ) {
        if (LocalWindowWidth.current == WindowSizeClass.FULL) {
            Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 12.dp, vertical = 24.dp).selectableGroup(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Image(painterResource(Res.drawable.komelia_icon), "Komelia", Modifier.padding(start = 12.dp).size(32.dp))
                Spacer(Modifier.height(24.dp))
                destinationItems.forEach { item ->
                    val active = selected == item.destination
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .background(if (active) colors.primaryContainer else Color.Transparent, RoundedCornerShape(14.dp))
                        .selectable(active, role = Role.Tab, onClick = { onSelect(item.destination, null) })
                        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                        Icon(item.icon, null, tint = if (active) colors.primary else colors.onSurfaceVariant)
                        Text(stringResource(item.label), color = if (active) colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
        } else NavigationRail(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets.navigationBars,
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                destinationItems.forEach { item ->
                    val interactions = remember(item.destination) { MutableInteractionSource() }
                    NavigationRailItem(
                        modifier = Modifier.komeliaHighlight(
                            selected == item.destination, KomeliaChromeStyle.navigationHighlightShape,
                            inset = KomeliaChromeStyle.navigationHighlightInset,
                            interactionSource = interactions,
                        ),
                        interactionSource = interactions,
                        selected = selected == item.destination,
                        onClick = { onSelect(item.destination, null) },
                        icon = { DestinationIcon(item, selected == item.destination) },
                        label = { NavigationDestinationLabel(stringResource(item.label), selected == item.destination) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            indicatorColor = colors.primaryContainer,
                            unselectedIconColor = if (glass) colors.onSurface else colors.onSurfaceVariant,
                            unselectedTextColor = if (glass) colors.onSurface else colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }

}

@Composable
private fun DestinationIcon(item: DestinationItem, isSelected: Boolean) {
    Icon(if (isSelected) item.selectedIcon else item.icon, contentDescription = null)
}
