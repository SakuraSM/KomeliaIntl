package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.updates.AppVersion

class AppVersionTest {
    @Test
    fun aboutVersionMatchesCurrentRelease() {
        assertEquals("0.18.15", AppVersion.current.toString())
    }
}
