package snd.komelia.ui.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryScreenSerializationTest {
    @Test
    fun selectedTabSurvivesJavaSerialization() {
        val restored = roundTrip(LibraryScreen(initialTab = LibraryTab.READ_LISTS))

        val initialTab = LibraryScreen::class.java.getDeclaredField("initialTab").run {
            isAccessible = true
            get(restored)
        }

        assertEquals(LibraryTab.READ_LISTS, initialTab)
    }

    @Test
    fun legacyMissingTabFallsBackToSeries() {
        assertEquals(LibraryTab.SERIES, resolveRestoredLibraryTab(null))
    }

    @Test
    fun serializationUidRemainsCompatibleWithExistingSavedState() {
        assertEquals(
            4825294352688731390L,
            ObjectStreamClass.lookup(LibraryScreen::class.java).serialVersionUID,
        )
    }

    private fun roundTrip(screen: LibraryScreen): LibraryScreen {
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(screen) }
        }.toByteArray()

        return ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as LibraryScreen
        }
    }
}
