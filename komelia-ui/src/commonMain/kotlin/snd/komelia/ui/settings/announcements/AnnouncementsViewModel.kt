package snd.komelia.ui.settings.announcements

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaAnnouncementsApi
import snd.komelia.ui.LoadState
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncement
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncementId

data class AnnouncementsState(val serverAnnouncements: List<KomgaAnnouncement>)

class AnnouncementsViewModel(
    private val appNotifications: AppNotifications,
    private val announcementsApi: KomgaAnnouncementsApi,
) : StateScreenModel<LoadState<AnnouncementsState>>(LoadState.Loading) {
    init {
        screenModelScope.launch {
            mutableState.value = loadServerAnnouncements(announcementsApi)
        }
    }

    fun markAsRead(id: KomgaAnnouncementId) {
        appNotifications.runCatchingToNotifications(screenModelScope) {
            announcementsApi.markAnnouncementsRead(listOf(id))
        }
    }
}

internal suspend fun loadServerAnnouncements(api: KomgaAnnouncementsApi): LoadState<AnnouncementsState> =
    try {
        LoadState.Success(AnnouncementsState(api.getAnnouncements().items))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        LoadState.Error(error)
    }
