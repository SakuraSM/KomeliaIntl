package snd.komelia.ui.settings

import kotlinx.browser.localStorage
import kotlinx.coroutines.test.runTest
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.repository.ReaderSettingsRepositoryWrapper
import snd.komelia.db.settings.LocalStorageSettingsRepository
import snd.komelia.db.settings.imageReaderKey
import snd.komelia.settings.model.ReaderType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderModeHintBrowserStorageTest {
    @Test fun legacyJsonAndStoredHintPreferencesSurviveBrowserRepositoryRecreation() = runTest {
        val previous = localStorage.getItem(imageReaderKey)
        try {
            localStorage.setItem(imageReaderKey, """{"readerType":"PANELS","cropBorders":true}""")
            val storage = LocalStorageSettingsRepository()
            val legacy = storage.getImageReaderSettings()
            assertTrue(legacy.readerModeHintsEnabled)
            assertNull(legacy.lastReaderModeHintSignature)
            assertEquals(ReaderType.PANELS, legacy.readerType)
            fun reopen(): ReaderSettingsRepositoryWrapper {
                val currentStorage = LocalStorageSettingsRepository()
                return ReaderSettingsRepositoryWrapper(SettingsStateWrapper(currentStorage.getImageReaderSettings(), currentStorage::saveImageReaderSettings))
            }
            assertTrue(reopen().consumeReaderModeHint("PANELS:RIGHT_TO_LEFT"))
            assertFalse(reopen().consumeReaderModeHint("PANELS:RIGHT_TO_LEFT"))
            reopen().putReaderModeHintsEnabled(false)
            assertFalse(reopen().consumeReaderModeHint("PAGED:LEFT_TO_RIGHT"))
            val disabled = storage.getImageReaderSettings()
            assertFalse(disabled.readerModeHintsEnabled)
            assertTrue(disabled.cropBorders)
            reopen().putReaderModeHintsEnabled(true)
            assertTrue(reopen().consumeReaderModeHint("PAGED:LEFT_TO_RIGHT"))
            assertFalse(reopen().consumeReaderModeHint("PAGED:LEFT_TO_RIGHT"))
        } finally {
            if (previous == null) localStorage.removeItem(imageReaderKey) else localStorage.setItem(imageReaderKey, previous)
        }
    }
}
