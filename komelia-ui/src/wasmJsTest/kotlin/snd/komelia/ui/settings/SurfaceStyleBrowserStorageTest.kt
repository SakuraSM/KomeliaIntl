package snd.komelia.ui.settings

import kotlinx.browser.localStorage
import snd.komelia.db.settings.LocalStorageSettingsRepository
import snd.komelia.db.settings.appSettingsKey
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SurfaceStyleBrowserStorageTest {
    @Test fun legacyBrowserSettingsAndEveryMaterialSurviveReopening() {
        val previous = localStorage.getItem(appSettingsKey)
        try {
            localStorage.setItem(appSettingsKey, """{"appTheme":"DARKER","cardWidth":150,"serverUrl":"https://fixture.invalid"}""")
            val storage = LocalStorageSettingsRepository()
            val legacy = storage.getSettings()
            assertEquals(AppSurfaceStyle.CLASSIC, legacy.surfaceStyle)
            assertFalse(legacy.reduceTransparency)
            for (theme in AppTheme.entries) {
                for (surface in AppSurfaceStyle.entries) {
                    for (reduce in listOf(false, true)) {
                        val next = legacy.copy(appTheme = theme, surfaceStyle = surface, reduceTransparency = reduce)
                        storage.saveAppSettings(next)
                        assertEquals(next, LocalStorageSettingsRepository().getSettings())
                    }
                }
            }
        } finally {
            if (previous == null) localStorage.removeItem(appSettingsKey) else localStorage.setItem(appSettingsKey, previous)
        }
    }
}
