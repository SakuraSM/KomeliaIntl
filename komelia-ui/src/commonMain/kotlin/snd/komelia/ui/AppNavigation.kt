package snd.komelia.ui

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.library.KomgaLibraryId

enum class AppDestination {
    LIBRARY,
    HOME,
    SEARCH,
    SETTINGS,
}

internal fun interface AppNavigationController {
    fun open(screen: Screen)
}

internal fun destinationStack(
    rootScreen: Screen,
    targetScreen: Screen,
): List<Screen> = when {
    targetScreen is LibraryScreen && targetScreen.libraryId != null -> listOf(rootScreen, targetScreen)
    targetScreen::class == rootScreen::class -> listOf(targetScreen)
    else -> listOf(rootScreen, targetScreen)
}

enum class NavigationPresentation {
    BottomBar,
    Rail,
}

enum class DestinationSelection {
    SwitchDestination,
    ReselectCurrent,
}

enum class AppBackAction {
    PopDetail,
    ReturnHome,
    ExitApp,
}

internal enum class ScreenTransitionAction {
    DisplayImmediately,
    AnimateChange,
    NoChange,
}

internal fun screenTransitionAction(
    displayedScreenKey: String?,
    targetScreenKey: String,
): ScreenTransitionAction = when {
    displayedScreenKey == null -> ScreenTransitionAction.DisplayImmediately
    displayedScreenKey == targetScreenKey -> ScreenTransitionAction.NoChange
    else -> ScreenTransitionAction.AnimateChange
}

internal fun immersiveScreenSaveableStateKey(screenKey: String): String =
    "immersive-screen:$screenKey"

internal fun <T> outermostNavigationTarget(
    current: T,
    parentOf: (T) -> T?,
): T {
    var target = current
    while (true) {
        target = parentOf(target) ?: return target
    }
}

internal fun Navigator.appRootNavigator(): Navigator =
    outermostNavigationTarget(this) { it.parent }

fun destinationSelection(
    current: AppDestination,
    selected: AppDestination,
): DestinationSelection =
    if (current == selected) DestinationSelection.ReselectCurrent else DestinationSelection.SwitchDestination

fun appBackAction(
    canPop: Boolean,
    destination: AppDestination,
): AppBackAction = when {
    canPop -> AppBackAction.PopDetail
    destination != AppDestination.HOME -> AppBackAction.ReturnHome
    else -> AppBackAction.ExitApp
}

fun navigationPresentation(windowSizeClass: WindowSizeClass): NavigationPresentation =
    if (windowSizeClass == WindowSizeClass.COMPACT) {
        NavigationPresentation.BottomBar
    } else {
        NavigationPresentation.Rail
    }

data class LibraryScopeState(
    val selectedLibraryId: KomgaLibraryId?,
    val availableLibraryIds: Set<KomgaLibraryId>,
) {
    val isSelectionAvailable: Boolean
        get() = selectedLibraryId == null || selectedLibraryId in availableLibraryIds

    fun reconciled(): LibraryScopeState =
        if (isSelectionAvailable) this else copy(selectedLibraryId = null)
}
