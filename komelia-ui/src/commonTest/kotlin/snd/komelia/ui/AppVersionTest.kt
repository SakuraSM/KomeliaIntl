package snd.komelia.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import snd.komelia.updates.AppVersion

class AppVersionTest {
    @Test
    fun aboutVersionMatchesCurrentRelease() {
        assertEquals("0.18.13", AppVersion.current.toString())
    }
}
