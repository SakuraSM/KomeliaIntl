package snd.komelia.ui.settings.offline

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.Navigator
import coil3.PlatformContext
import kotlinx.coroutines.flow.SharedFlow
import snd.komelia.AppNotifications
import snd.komelia.KomgaAuthenticationState
import snd.komelia.offline.server.actions.MediaServerDeleteAction
import snd.komelia.offline.api.OfflineLibraryApi
import snd.komelia.offline.server.repository.OfflineMediaServerRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.DownloadEvent
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.offline.tasks.OfflineTaskEmitter
import snd.komelia.offline.tasks.repository.OfflineTasksRepository
import snd.komelia.offline.book.actions.BookDeleteAction
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komelia.offline.media.repository.OfflineMediaRepository
import snd.komelia.offline.series.actions.SeriesDeleteAction
import snd.komelia.offline.series.repository.OfflineSeriesRepository
import snd.komelia.offline.user.actions.UserDeleteAction
import snd.komelia.offline.user.repository.OfflineUserRepository
import snd.komelia.ui.settings.offline.downloads.OfflineDownloadsState
import snd.komelia.ui.settings.offline.cache.OfflineCacheState
import snd.komelia.ui.settings.offline.logs.OfflineLogsState
import snd.komelia.ui.settings.offline.users.OfflineUsersState

class OfflineSettingsViewModel(
    private val authState: KomgaAuthenticationState,
    private val appNotifications: AppNotifications,
    private val offlineSettingsRepository: OfflineSettingsRepository,
    private val userRepository: OfflineUserRepository,
    private val serverRepository: OfflineMediaServerRepository,
    private val offlineLibraryApi: OfflineLibraryApi,
    private val logJournalRepository: LogJournalRepository,
    private val serverDeleteAction: MediaServerDeleteAction,
    private val userDeleteAction: UserDeleteAction,
    private val platformContext: PlatformContext,
    bookRepository: OfflineBookRepository,
    seriesRepository: OfflineSeriesRepository,
    mediaRepository: OfflineMediaRepository,
    bookDeleteAction: BookDeleteAction,
    seriesDeleteAction: SeriesDeleteAction,

    private val taskEmitter: OfflineTaskEmitter,
    private val tasksRepository: OfflineTasksRepository,
    private val downloadEvents: SharedFlow<DownloadEvent>,
) : ScreenModel {

    val usersState = OfflineUsersState(
        authState = authState,
        appNotifications = appNotifications,
        offlineSettingsRepository = offlineSettingsRepository,
        userRepository = userRepository,
        serverRepository = serverRepository,
        offlineLibraryApi = offlineLibraryApi,
        logJournalRepository = logJournalRepository,
        coroutineScope = screenModelScope,
        userDeleteAction = userDeleteAction,
        serverDeleteAction = serverDeleteAction
    )

    val logsState = OfflineLogsState(
        logJournalRepository = logJournalRepository,
        coroutineScope = screenModelScope,
    )
    val downloadsSate = OfflineDownloadsState(
        downloadEvents = downloadEvents,
        taskEmitter = taskEmitter,
        tasksRepository = tasksRepository,
        settingsRepository = offlineSettingsRepository,
        platformContext = platformContext,
        coroutineScope = screenModelScope,
    )
    internal val cacheState = OfflineCacheState(
        bookRepository = bookRepository,
        seriesRepository = seriesRepository,
        mediaRepository = mediaRepository,
        bookDeleteAction = bookDeleteAction,
        seriesDeleteAction = seriesDeleteAction,
        notifications = appNotifications,
        coroutineScope = screenModelScope,
    )

    suspend fun initialize(rootNavigator: Navigator) {
        usersState.initialize(rootNavigator)
        logsState.initialize()
    }

    suspend fun initializeCache() = cacheState.initialize()
}
