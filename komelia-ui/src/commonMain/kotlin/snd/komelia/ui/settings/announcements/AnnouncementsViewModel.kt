package snd.komelia.ui.settings.announcements

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import snd.komelia.AppNotifications
import snd.komelia.komga.api.KomgaAnnouncementsApi
import snd.komelia.ui.LoadState
import snd.komelia.ui.LoadState.Error
import snd.komelia.ui.LoadState.Loading
import snd.komelia.ui.LoadState.Success
import snd.komelia.updates.AppProjectMetadata
import snd.komelia.updates.GithubRelease
import snd.komelia.updates.UpdateClient
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncement
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncementId

enum class AnnouncementSource {
    Project,
    Upstream,
    Server,
}

data class AnnouncementsState(
    val projectReleases: List<GithubRelease>,
    val upstreamReleases: List<GithubRelease>,
    val serverAnnouncements: List<KomgaAnnouncement>,
    val unavailableSources: Set<AnnouncementSource>,
)

class AnnouncementsViewModel(
    private val appNotifications: AppNotifications,
    private val announcementsApi: KomgaAnnouncementsApi,
    private val updateClient: UpdateClient,
) : StateScreenModel<LoadState<AnnouncementsState>>(Loading) {

    init {
        screenModelScope.launch {
            mutableState.value = coroutineScope {
                val projectReleases = async {
                    runCatching { updateClient.getReleases(AppProjectMetadata.releasesApiUrl) }
                }
                val upstreamReleases = async {
                    runCatching { updateClient.getReleases(AppProjectMetadata.upstreamReleasesApiUrl) }
                }
                val serverAnnouncements = async {
                    runCatching { announcementsApi.getAnnouncements().items }
                }

                announcementsLoadState(
                    projectResult = projectReleases.await(),
                    upstreamResult = upstreamReleases.await(),
                    serverResult = serverAnnouncements.await(),
                )
            }
        }
    }

    fun markAsRead(id: KomgaAnnouncementId) {
        appNotifications.runCatchingToNotifications(screenModelScope) {
            announcementsApi.markAnnouncementsRead(listOf(id))
        }
    }
}

internal fun announcementsLoadState(
    projectResult: Result<List<GithubRelease>>,
    upstreamResult: Result<List<GithubRelease>>,
    serverResult: Result<List<KomgaAnnouncement>>,
): LoadState<AnnouncementsState> {
    val results = listOf(projectResult, upstreamResult, serverResult)
    if (results.all { it.isFailure }) {
        return Error(results.firstNotNullOf { it.exceptionOrNull() })
    }

    val unavailableSources = buildSet {
        if (projectResult.isFailure) add(AnnouncementSource.Project)
        if (upstreamResult.isFailure) add(AnnouncementSource.Upstream)
        if (serverResult.isFailure) add(AnnouncementSource.Server)
    }
    return Success(
        AnnouncementsState(
            projectReleases = projectResult.getOrDefault(emptyList()),
            upstreamReleases = upstreamResult.getOrDefault(emptyList()),
            serverAnnouncements = serverResult.getOrDefault(emptyList()),
            unavailableSources = unavailableSources,
        )
    )
}
