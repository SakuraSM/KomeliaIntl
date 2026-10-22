package snd.komelia.ui.settings.appearance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppearancePreferenceTest {
    @Test
    fun aSlowEarlierWriteCannotReplaceTheLastSurfaceChoice() = runTest {
        val pending = CompletableDeferred<Unit>()
        val writes = mutableListOf<AppSurfaceStyle>()
        val preference = AppearancePreference(AppSurfaceStyle.CLASSIC, { this }) {
            if (writes.isEmpty()) pending.await()
            writes.add(it)
        }
        preference.update(AppSurfaceStyle.LIQUID_GLASS)
        runCurrent()
        preference.update(AppSurfaceStyle.CLASSIC)
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(AppSurfaceStyle.LIQUID_GLASS, AppSurfaceStyle.CLASSIC), writes)
        assertEquals(AppSurfaceStyle.CLASSIC, preference.value)
        assertFalse(preference.hasSaveError)
    }

    @Test
    fun failedSaveRestoresTheSavedValueAndCanBeRetried() = runTest {
        var shouldFail = true
        var stored = AppSurfaceStyle.CLASSIC
        val preference = AppearancePreference(stored, { this }) {
            if (shouldFail) error("Synthetic write failure")
            stored = it
        }
        preference.update(AppSurfaceStyle.LIQUID_GLASS)
        advanceUntilIdle()
        assertEquals(AppSurfaceStyle.CLASSIC, preference.value)
        assertEquals(AppSurfaceStyle.CLASSIC, stored)
        assertTrue(preference.hasSaveError)

        shouldFail = false
        preference.retry()
        advanceUntilIdle()
        assertEquals(AppSurfaceStyle.LIQUID_GLASS, stored)
        assertEquals(stored, preference.value)
        assertFalse(preference.hasSaveError)
    }

    @Test
    fun anObsoleteFailureDoesNotDiscardANewerThemeChoice() = runTest {
        val pending = CompletableDeferred<Unit>()
        var firstWrite = true
        var stored = AppTheme.DARK
        val preference = AppearancePreference(stored, { this }) {
            if (firstWrite) {
                firstWrite = false
                pending.await()
                error("Synthetic obsolete failure")
            }
            stored = it
        }
        preference.update(AppTheme.LIGHT)
        runCurrent()
        preference.update(AppTheme.DARKER)
        pending.complete(Unit)
        advanceUntilIdle()
        assertEquals(AppTheme.DARKER, stored)
        assertEquals(stored, preference.value)
        assertFalse(preference.hasSaveError)
    }
}
