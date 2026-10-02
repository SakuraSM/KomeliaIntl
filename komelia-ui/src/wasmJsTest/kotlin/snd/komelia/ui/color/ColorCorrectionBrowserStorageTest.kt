package snd.komelia.ui.color

import com.juul.indexeddb.openDatabase
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import snd.komelia.color.*
import snd.komelia.db.Key
import snd.komelia.db.color.IDBBookColorCorrectionRepository
import snd.komelia.db.color.jsModel.JsBookColorCorrection
import snd.komelia.db.settings.LocalStorageSettingsRepository
import snd.komelia.db.settings.imageReaderKey
import snd.komga.client.book.KomgaBookId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ColorCorrectionBrowserStorageTest {
    @Test fun legacyIndexedDbRecordsStayCustomAndPoliciesSurviveReopen() = runTest {
        val name = "komelia-color-test-${Uuid.random()}"
        val database = openDatabase(name, 1) { database, _, _ ->
            database.createObjectStore("bookColorCorrection")
            database.createObjectStore("bookColorCurves")
            database.createObjectStore("bookColorLevels")
        }
        val book = KomgaBookId("legacy")
        val record = legacyRecord()
        database.writeTransaction("bookColorCorrection") {
            objectStore("bookColorCorrection").put(record, Key(book.value))
        }
        val repository = IDBBookColorCorrectionRepository(database)
        assertEquals(BookColorCorrectionMode.CUSTOM, repository.getMode(book).first())
        val levels = BookColorLevels(book, ColorLevelChannels.DEFAULT.copy(color = ColorLevelsConfig.DEFAULT.copy(gamma = 2f)))
        repository.saveConfiguration(book, ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS, levels = levels.channels))
        repository.setMode(book, BookColorCorrectionMode.DISABLED)
        database.close()
        val reopened = openDatabase(name, 1) { _, _, _ -> error("Must not upgrade on reopen") }
        try {
            val restored = IDBBookColorCorrectionRepository(reopened)
            assertEquals(BookColorCorrectionMode.DISABLED, restored.getMode(book).first())
            assertEquals(levels, restored.getLevels(book).first())
            restored.setMode(book, BookColorCorrectionMode.INHERIT)
            assertEquals(BookColorCorrectionMode.INHERIT, restored.getMode(book).first())
            assertEquals(levels, restored.getLevels(book).first())
            assertEquals(BookColorCorrectionMode.INHERIT, restored.getMode(KomgaBookId("new")).first())
        } finally { reopened.close() }
    }

    @Test fun browserSettingsReadOldJsonAndRetainTheDefaultSnapshot() {
        val previous = localStorage.getItem(imageReaderKey)
        try {
            localStorage.setItem(imageReaderKey, """{"cropBorders":true}""")
            val storage = LocalStorageSettingsRepository()
            val old = storage.getImageReaderSettings()
            assertNull(old.defaultColorCorrection)
            val correction = DefaultColorCorrection("Saved", ColorCorrectionConfig(ColorCorrectionType.COLOR_CURVES))
            storage.saveImageReaderSettings(old.copy(defaultColorCorrection = correction))
            assertEquals(correction, LocalStorageSettingsRepository().getImageReaderSettings().defaultColorCorrection)
        } finally {
            if (previous == null) localStorage.removeItem(imageReaderKey) else localStorage.setItem(imageReaderKey, previous)
        }
    }
}

private fun legacyRecord(): JsBookColorCorrection = js("({bookId: 'legacy', type: 'COLOR_LEVELS'})")
