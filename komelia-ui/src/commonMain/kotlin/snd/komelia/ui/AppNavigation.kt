package snd.komelia.ui

import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.library.KomgaLibraryId

enum class AppDestination {
    LIBRARY,
    HOME,
    SEARCH,
    SETTINGS,
}

enum class NavigationPresentation {
    BottomBar,
    Rail,
}

enum class DestinationSelection {
    SwitchDestination,
    ReselectCurrent,
}

fun destinationSelection(
    current: AppDestination,
    selected: AppDestination,
): DestinationSelection =
    if (current == selected) DestinationSelection.ReselectCurrent else DestinationSelection.SwitchDestination

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
