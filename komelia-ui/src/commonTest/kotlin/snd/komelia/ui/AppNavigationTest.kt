package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import snd.komelia.ui.BookSiblingsContext
import snd.komelia.ui.book.BookScreen
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.platform.WindowSizeClass
import snd.komelia.ui.search.SearchScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komga.client.book.KomgaBookId
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId

class AppNavigationTest {
    private data class NavigationNode(
        val name: String,
        val parent: NavigationNode? = null,
    )

    @Test
    fun compactUsesBottomBarAndLargerWindowsUseRail() {
        assertEquals(NavigationPresentation.BottomBar, navigationPresentation(WindowSizeClass.COMPACT))
        assertEquals(NavigationPresentation.Rail, navigationPresentation(WindowSizeClass.MEDIUM))
        assertEquals(NavigationPresentation.Rail, navigationPresentation(WindowSizeClass.EXPANDED))
        assertEquals(NavigationPresentation.Rail, navigationPresentation(WindowSizeClass.FULL))
    }

    @Test
    fun primaryNavigationContainsFiveStableDestinations() {
        assertEquals(
            listOf(
                AppDestination.LIBRARY,
                AppDestination.HOME,
                AppDestination.LOCAL,
                AppDestination.SEARCH,
                AppDestination.SETTINGS,
            ),
            AppDestination.entries,
        )
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
    fun backActionPopsDetailsReturnsOtherRootsHomeAndLetsHomeExit() {
        assertEquals(AppBackAction.PopDetail, appBackAction(canPop = true, AppDestination.SEARCH))
        assertEquals(AppBackAction.ReturnHome, appBackAction(canPop = false, AppDestination.LIBRARY))
        assertEquals(AppBackAction.ReturnHome, appBackAction(canPop = false, AppDestination.LOCAL))
        assertEquals(AppBackAction.ReturnHome, appBackAction(canPop = false, AppDestination.SEARCH))
        assertEquals(AppBackAction.ReturnHome, appBackAction(canPop = false, AppDestination.SETTINGS))
        assertEquals(AppBackAction.ExitApp, appBackAction(canPop = false, AppDestination.HOME))
    }

    @Test
    fun screenTransitionSkipsInitialMountAndSameScreenReset() {
        assertEquals(
            ScreenTransitionAction.DisplayImmediately,
            screenTransitionAction(displayedScreenKey = null, targetScreenKey = "library-root"),
        )
        assertEquals(
            ScreenTransitionAction.NoChange,
            screenTransitionAction(displayedScreenKey = "library-root", targetScreenKey = "library-root"),
        )
        assertEquals(
            ScreenTransitionAction.AnimateChange,
            screenTransitionAction(displayedScreenKey = "library-root", targetScreenKey = "series-detail"),
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

    @Test
    fun repeatedAuthenticationNavigationAlwaysTargetsTheOutermostNavigator() {
        val root = NavigationNode("app-root")
        val tabs = NavigationNode("tabs", root)
        val settings = NavigationNode("settings", tabs)

        repeat(10) {
            assertEquals(root, outermostNavigationTarget(settings) { it.parent })
        }
    }

    @Test
    fun libraryDetailsKeepTheLibraryRootUnderTheTargetScreen() {
        val root = LibraryScreen()
        val book = BookScreen(KomgaBookId("book-1"), BookSiblingsContext.Series)
        val series = SeriesScreen(KomgaSeriesId("series-1"))

        assertEquals(listOf(root, book), destinationStack(root, book))
        assertEquals(listOf(root, series), destinationStack(root, series))
    }

    @Test
    fun aSpecificLibraryKeepsAllLibrariesAsItsBackTarget() {
        val root = LibraryScreen()
        val selectedLibrary = LibraryScreen(KomgaLibraryId("library-1"))

        assertEquals(listOf(root, selectedLibrary), destinationStack(root, selectedLibrary))
    }

    @Test
    fun sameTypeParameterizedRootsAreNotDuplicated() {
        val root = SearchScreen(null)
        val query = SearchScreen("komelia")

        assertEquals(listOf(query), destinationStack(root, query))
    }

    @Test
    fun immersiveSaveableStateKeysAreScopedToTheTargetScreen() {
        assertEquals(
            immersiveScreenSaveableStateKey("image-reader"),
            immersiveScreenSaveableStateKey("image-reader"),
        )
        assertNotEquals(
            immersiveScreenSaveableStateKey("image-reader"),
            immersiveScreenSaveableStateKey("epub-reader"),
        )
    }
}
