package snd.komelia.ui.settings.imagereader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snd.komelia.AppNotifications
import snd.komelia.settings.ImageReaderSettingsRepository

/** Shared by the application settings page and the reader's inline controls. */
class ReaderModeHintPreference(
    private val repository: ImageReaderSettingsRepository,
    private val notifications: AppNotifications,
    private val scope: CoroutineScope,
) {
    val enabled = MutableStateFlow<Boolean?>(null)
    val isSaving = MutableStateFlow(false)
    suspend fun initialize() { enabled.value = repository.getReaderModeHintsEnabled().first() }
    fun change(value: Boolean) {
        if (!scope.isActive || enabled.value == null || isSaving.value) return
        isSaving.value = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(NonCancellable) {
                    notifications.runCatchingToNotifications { repository.putReaderModeHintsEnabled(value) }
                        .onSuccess { enabled.value = value }
                }
            } finally { isSaving.value = false }
        }
    }
}
