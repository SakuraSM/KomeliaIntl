package snd.komelia.ui.settings.announcements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import snd.komelia.ui.LoadState
import snd.komelia.updates.GithubRelease
import snd.komga.client.announcements.KomgaJsonFeed.KomgaAnnouncement

class AnnouncementsViewModelTest {
    @Test
    fun keepsProjectAndUpstreamReleaseNotesAsSeparateSources() {
        val projectRelease = release(1, "v0.18.15")
        val upstreamRelease = release(2, "v0.18.14")

        val result = announcementsLoadState(
            projectResult = Result.success(listOf(projectRelease)),
            upstreamResult = Result.success(listOf(upstreamRelease)),
            serverResult = Result.success(emptyList()),
        )

        val state = assertIs<LoadState.Success<AnnouncementsState>>(result).value
        assertEquals(listOf(projectRelease), state.projectReleases)
        assertEquals(listOf(upstreamRelease), state.upstreamReleases)
        assertTrue(state.unavailableSources.isEmpty())
    }

    @Test
    fun oneFailedSourceDoesNotHideTheOtherReleaseNotes() {
        val upstreamRelease = release(2, "v0.18.14")

        val result = announcementsLoadState(
            projectResult = Result.failure(IllegalStateException("project unavailable")),
            upstreamResult = Result.success(listOf(upstreamRelease)),
            serverResult = Result.success(emptyList()),
        )

        val state = assertIs<LoadState.Success<AnnouncementsState>>(result).value
        assertTrue(state.projectReleases.isEmpty())
        assertEquals(listOf(upstreamRelease), state.upstreamReleases)
        assertEquals(setOf(AnnouncementSource.Project), state.unavailableSources)
    }

    @Test
    fun allFailedSourcesProduceAnErrorState() {
        val result = announcementsLoadState(
            projectResult = Result.failure(IllegalStateException("project unavailable")),
            upstreamResult = Result.failure(IllegalStateException("upstream unavailable")),
            serverResult = Result.failure<List<KomgaAnnouncement>>(IllegalStateException("server unavailable")),
        )

        assertIs<LoadState.Error>(result)
    }

    private fun release(id: Int, tag: String) = GithubRelease(
        id = id,
        publishedAt = Instant.parse("2026-08-23T00:00:00Z"),
        tagName = tag,
        htmlUrl = "https://example.com/$id",
        body = "Release notes",
        assets = emptyList(),
    )
}
