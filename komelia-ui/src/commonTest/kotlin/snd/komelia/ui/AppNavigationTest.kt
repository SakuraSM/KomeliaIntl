package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import snd.komelia.ui.platform.WindowSizeClass
import snd.komga.client.library.KomgaLibraryId

class AppNavigationTest {
    @Test
    fun compactUsesBottomBarAndLargerWindowsUseRail() {
        assertEquals(NavigationPresentation.BottomBar, navigationPresentation(WindowSizeClass.COMPACT))
        assertEquals(NavigationPresentation.Rail, navigationPresentation(WindowSizeClass.MEDIUM))
        assertEquals(NavigationPresentation.Rail, navigationPresentation(WindowSizeClass.EXPANDED))
        assertEquals(NavigationPresentation.Rail, navigationPresentation(WindowSizeClass.FULL))
    }

    @Test
    fun destinationSelectionDistinguishesSwitchesFromReselection() {
        assertEquals(
            DestinationSelection.SwitchDestination,
            destinationSelection(AppDestination.HOME, AppDestination.SEARCH),
        )
        assertEquals(
            DestinationSelection.ReselectCurrent,
            destinationSelection(AppDestination.LIBRARY, AppDestination.LIBRARY),
        )
    }

    @Test
    fun missingLibraryFallsBackToAllLibraries() {
        val selected = KomgaLibraryId("missing")
        val state = LibraryScopeState(selected, setOf(KomgaLibraryId("available")))

        assertFalse(state.isSelectionAvailable)
        assertNull(state.reconciled().selectedLibraryId)
        assertTrue(state.reconciled().isSelectionAvailable)
    }
}
