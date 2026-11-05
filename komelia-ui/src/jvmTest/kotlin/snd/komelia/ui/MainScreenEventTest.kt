package snd.komelia.ui

import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import snd.komelia.AppNotifications
import snd.komelia.ui.topbar.NotificationsState
import snd.komelia.ui.topbar.SearchBarState
import snd.komga.client.sse.KomgaEvent

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class MainScreenEventTest {
    @Test fun queueStatusCanArriveWhileTheScreenModelIsCreated() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val events = MutableSharedFlow<KomgaEvent>(replay = 1)
        val status = KomgaEvent.TaskQueueStatus(3, mapOf("SCAN" to 3))
        assertTrue(events.tryEmit(status))
        val holder = "main-events-${java.util.UUID.randomUUID()}"
        try {
            val model = ScreenModelStore.getOrPut(holder, null) {
                MainScreenViewModel(
                    komgaEvents = events,
                    screenReloadFlow = MutableSharedFlow(),
                    offlineSettingsRepository = null,
                    searchBarState = SearchBarState(noApi(), noApi(), AppNotifications(), MutableStateFlow(emptyList())),
                    notificationsState = NotificationsState(events, null),
                )
            }
            assertEquals(status, model.komgaTaskQueueStatus.value)
            val nextStatus = KomgaEvent.TaskQueueStatus(0, emptyMap())
            events.emit(nextStatus)
            assertEquals(nextStatus, model.komgaTaskQueueStatus.value)
        } finally {
            ScreenModelStore.onDisposeNavigator(holder)
            Dispatchers.resetMain()
        }
    }

    private inline fun <reified T> noApi(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, method, _ -> error("Unexpected call to ${method.name}") } as T
}
