package snd.komelia.db.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import snd.komelia.db.SettingsStateWrapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsStateWrapperTest {
    private data class Preferences(val cardWidth: Int = 240, val lightTheme: Boolean = false)

    @Test fun failedSaveDoesNotPublishUnpersistedSettings() = runBlocking {
        val wrapper = SettingsStateWrapper(Preferences()) { throw java.io.IOException("synthetic write failure") }
        assertFailsWith<java.io.IOException> { wrapper.transform { it.copy(cardWidth = 350) } }
        assertEquals(Preferences(), wrapper.state.value)
    }

    @Test fun concurrentPreferenceUpdatesDoNotOverwriteEachOther() = runBlocking {
        val releaseFirstSave = CompletableDeferred<Unit>()
        var first = true
        val wrapper = SettingsStateWrapper(Preferences()) {
            if (first) { first = false; releaseFirstSave.await() }
        }
        val card = async(start = CoroutineStart.UNDISPATCHED) { wrapper.transform { it.copy(cardWidth = 350) } }
        val theme = async(start = CoroutineStart.UNDISPATCHED) { wrapper.transform { it.copy(lightTheme = true) } }
        releaseFirstSave.complete(Unit)
        card.await()
        theme.await()
        assertEquals(Preferences(350, true), wrapper.state.value)
    }
}
