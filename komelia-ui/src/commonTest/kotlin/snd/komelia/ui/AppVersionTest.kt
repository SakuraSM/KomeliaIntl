package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import snd.komelia.updates.AppVersion

class AppVersionTest {
    @Test
    fun aboutVersionMatchesCurrentRelease() {
        assertEquals("0.18.16", AppVersion.current.toString())
    }

    @Test
    fun parsesPlainAndGithubTagVersions() {
        val expected = AppVersion(0, 18, 15)

        assertEquals(expected, AppVersion.fromString("0.18.15"))
        assertEquals(expected, AppVersion.fromString("v0.18.15"))
        assertEquals(expected, AppVersion.fromString("V0.18.15"))
        assertEquals(expected, AppVersion.fromString("  v0.18.15  "))
    }

    @Test
    fun parsesTwoPartAndPreReleaseVersions() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.fromString("1.2"))
        assertEquals(AppVersion(1, 2, 3), AppVersion.fromString("v1.2.3-beta.1"))
    }

    @Test
    fun rejectsMalformedVersionsWithDomainError() {
        assertFailsWith<IllegalStateException> { AppVersion.fromString("V0") }
        assertFailsWith<IllegalStateException> { AppVersion.fromString("release-0.18.15") }
    }
}
