package snd.komelia.ui

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import cafe.adriel.voyager.navigator.Navigator
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import snd.komelia.AppNotifications
import snd.komelia.ui.library.LibraryScreen
import snd.komelia.ui.oneshot.OneshotScreen
import snd.komelia.ui.series.SeriesScreen
import snd.komelia.ui.topbar.NotificationsState
import snd.komelia.ui.topbar.SearchBarState
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.series.KomgaSeriesId
import snd.komga.client.sse.KomgaEvent

@OptIn(InternalVoyagerApi::class)
class MainScreenDeleteEventTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deletionArrivingBeforeNavigatorInitializationIsAppliedAfterward() {
        val current = KomgaSeriesId("current")
        val library = KomgaLibraryId("library")
        val events = MutableSharedFlow<KomgaEvent>(replay = 1)
        assertTrue(events.tryEmit(KomgaEvent.SeriesDeleted(current, library)))
        val holder = "main-pending-delete-${java.util.UUID.randomUUID()}"
        lateinit var navigator: Navigator
        compose.setContent {
            Navigator(SeriesScreen(current)) { value -> SideEffect { navigator = value } }
        }
        try {
            compose.runOnIdle {
                val model = ScreenModelStore.getOrPut(holder, null) {
                    MainScreenViewModel(
                        komgaEvents = events,
                        screenReloadFlow = MutableSharedFlow(),
                        offlineSettingsRepository = null,
                        searchBarState = SearchBarState(noApi(), noApi(), AppNotifications(), MutableStateFlow(emptyList())),
                        notificationsState = NotificationsState(events, null),
                    )
                }
                model.initialize(navigator)
            }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(library, assertIs<LibraryScreen>(navigator.lastItem).libraryId) }
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
        }
    }

    @Test fun deletingAnotherSeriesDoesNotLeaveTheCurrentSeries() {
        val current = KomgaSeriesId("current")
        val other = KomgaSeriesId("other")
        val library = KomgaLibraryId("library")
        val events = MutableSharedFlow<KomgaEvent>(extraBufferCapacity = 4)
        val holder = "main-delete-${java.util.UUID.randomUUID()}"
        lateinit var navigator: Navigator
        lateinit var model: MainScreenViewModel

        compose.setContent {
            Navigator(SeriesScreen(current)) { value -> SideEffect { navigator = value } }
        }
        try {
            compose.runOnIdle {
                model = ScreenModelStore.getOrPut(holder, null) {
                    MainScreenViewModel(
                        komgaEvents = events,
                        screenReloadFlow = MutableSharedFlow(),
                        offlineSettingsRepository = null,
                        searchBarState = SearchBarState(noApi(), noApi(), AppNotifications(), MutableStateFlow(emptyList())),
                        notificationsState = NotificationsState(events, null),
                    )
                }
                model.initialize(navigator)
                assertTrue(events.tryEmit(KomgaEvent.SeriesDeleted(other, library)))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(current, assertIs<SeriesScreen>(navigator.lastItem).seriesId)
                assertTrue(events.tryEmit(KomgaEvent.SeriesDeleted(current, library)))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(library, assertIs<LibraryScreen>(navigator.lastItem).libraryId)
                navigator.replaceAll(OneshotScreen(current, BookSiblingsContext.Series))
                assertTrue(events.tryEmit(KomgaEvent.SeriesDeleted(other, library)))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(current, assertIs<OneshotScreen>(navigator.lastItem).seriesId)
                assertTrue(events.tryEmit(KomgaEvent.SeriesDeleted(current, library)))
            }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(library, assertIs<LibraryScreen>(navigator.lastItem).libraryId) }
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
        }
    }

    private inline fun <reified T> noApi(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("Unexpected call to ${method.name}") } as T
}
