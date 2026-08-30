package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import snd.komelia.updates.AppVersion

class AppVersionTest {
    @Test
    fun aboutVersionMatchesCurrentRelease() {
        assertEquals("0.20.0-beta.1", AppVersion.current.toString())
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
        assertEquals(AppVersion(1, 2, 3, "beta.1"), AppVersion.fromString("v1.2.3-beta.1"))
        assertEquals("1.2.3-beta.1", AppVersion.fromString("v1.2.3-beta.1+build.7").toString())
    }

    @Test
    fun comparesPreReleaseVersionsUsingSemanticVersioningRules() {
        assertTrue(AppVersion.fromString("0.20.0-beta.1") > AppVersion.fromString("0.19.4"))
        assertTrue(AppVersion.fromString("0.20.0-beta.1") < AppVersion.fromString("0.20.0-beta.2"))
        assertTrue(AppVersion.fromString("0.20.0-beta.2") < AppVersion.fromString("0.20.0-rc.1"))
        assertTrue(AppVersion.fromString("0.20.0-rc.1") < AppVersion.fromString("0.20.0"))
        assertTrue(AppVersion.fromString("1.0.0-beta.2") < AppVersion.fromString("1.0.0-beta.10"))
    }

    @Test
    fun rejectsMalformedVersionsWithDomainError() {
        assertFailsWith<IllegalStateException> { AppVersion.fromString("V0") }
        assertFailsWith<IllegalStateException> { AppVersion.fromString("release-0.18.15") }
        assertFailsWith<IllegalStateException> { AppVersion.fromString("1.0.0-beta..1") }
        assertFailsWith<IllegalStateException> { AppVersion.fromString("1.0.0-01") }
    }
}
