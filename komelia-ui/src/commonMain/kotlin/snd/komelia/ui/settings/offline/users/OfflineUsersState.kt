package snd.komelia.ui.settings.offline.users

import cafe.adriel.voyager.navigator.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import snd.komelia.AppNotifications
import snd.komelia.KomgaAuthenticationState
import snd.komelia.offline.api.OfflineLibraryApi
import snd.komelia.offline.server.actions.MediaServerDeleteAction
import snd.komelia.offline.server.model.OfflineMediaServer
import snd.komelia.offline.server.model.OfflineMediaServerId
import snd.komelia.offline.server.repository.OfflineMediaServerRepository
import snd.komelia.offline.settings.OfflineSettingsRepository
import snd.komelia.offline.sync.model.OfflineLogEntry
import snd.komelia.offline.sync.repository.LogJournalRepository
import snd.komelia.offline.user.actions.UserDeleteAction
import snd.komelia.offline.user.model.OfflineUser
import snd.komelia.offline.user.repository.OfflineUserRepository
import snd.komelia.ui.login.LoginScreen
import snd.komelia.ui.MainScreen
import snd.komelia.ui.settings.offline.OfflineOperationLogger
import snd.komga.client.user.KomgaUserId

class OfflineUsersState(
    private val authState: KomgaAuthenticationState,
    private val appNotifications: AppNotifications,
    private val offlineSettingsRepository: OfflineSettingsRepository,
    private val userRepository: OfflineUserRepository,
    private val serverRepository: OfflineMediaServerRepository,
    private val offlineLibraryApi: OfflineLibraryApi,
    logJournalRepository: LogJournalRepository,

    private val userDeleteAction: UserDeleteAction,
    private val serverDeleteAction: MediaServerDeleteAction,

    private val coroutineScope: CoroutineScope,
) {
    private val operationLogger = OfflineOperationLogger(logJournalRepository, coroutineScope)

    val isOffline = offlineSettingsRepository.getOfflineMode()
    val offlineUsers = MutableStateFlow<Map<OfflineMediaServer, List<OfflineUser>>>(emptyMap())
    val currentUser = authState.authenticatedUser
    val onlineServerUrl = authState.serverUrl

    private val rootNavigator = MutableStateFlow<Navigator?>(null)

    suspend fun initialize(rootNavigator: Navigator) {
        this.rootNavigator.value = rootNavigator
        loadServers()
    }

    fun goOnline() {
        appNotifications.runCatchingToNotifications(
            coroutineScope = coroutineScope,
            onFailure = { operationLogger.record(OfflineLogEntry.Operation.GO_ONLINE, it) },
        ) {
            offlineSettingsRepository.putOfflineMode(false)
            authState.reset()
            rootNavigator.value?.replaceAll(LoginScreen())
        }
    }

    private suspend fun loadServers() {
        val servers = serverRepository.findAll()
        val users = servers.associateWith { userRepository.findAllByServer(it.id) }
        offlineUsers.value = users
    }

    fun loginAs(userId: KomgaUserId) {
        appNotifications.runCatchingToNotifications(
            coroutineScope = coroutineScope,
            onFailure = { operationLogger.record(OfflineLogEntry.Operation.USER_SWITCH, it) },
        ) {

            val offlineUser = if (userId == OfflineUser.ROOT) {
                OfflineUser.ROOT_USER
            } else {
                userRepository.get(userId)
            }

            offlineSettingsRepository.putUserId(offlineUser.id)
            offlineSettingsRepository.putOfflineMode(true)
            authState.setStateValues(offlineUser.toKomgaUser(), offlineLibraryApi.getLibraries())
            rootNavigator.value?.replaceAll(MainScreen())
        }
    }

    fun onServerDelete(serverId: OfflineMediaServerId) {
        appNotifications.runCatchingToNotifications(
            coroutineScope = coroutineScope,
            onFailure = { operationLogger.record(OfflineLogEntry.Operation.DELETE_SERVER, it) },
        ) {
            serverDeleteAction.execute(serverId)
            loadServers()
        }
    }

    fun onUserDelete(userId: KomgaUserId) {
        appNotifications.runCatchingToNotifications(
            coroutineScope = coroutineScope,
            onFailure = { operationLogger.record(OfflineLogEntry.Operation.DELETE_USER, it) },
        ) {
            userDeleteAction.execute(userId)
            loadServers()
        }
    }
}
