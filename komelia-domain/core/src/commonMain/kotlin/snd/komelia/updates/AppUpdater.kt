package snd.komelia.updates

import kotlinx.coroutines.flow.Flow

interface AppUpdater {
    val installsInApp: Boolean get() = false


    suspend fun getReleases(): List<AppRelease>

    suspend fun updateToLatest(): Flow<UpdateProgress>?

    fun updateTo(release: AppRelease): Flow<UpdateProgress>?
}
