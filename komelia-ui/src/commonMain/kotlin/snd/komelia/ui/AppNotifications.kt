package snd.komelia.ui

import com.dokar.sonner.Toast
import com.dokar.sonner.ToastType
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.*
import snd.komelia.AppNotification
import snd.komelia.AppNotificationMessage
import snd.komelia.AppNotificationMessageKey
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds

suspend fun AppNotification.toToast(): Toast {
    val localizedMessage = message.localizedText()
    return when (this) {
        is AppNotification.Error -> Toast(id = id, message = localizedMessage, type = ToastType.Error, duration = 5.seconds)
        is AppNotification.Success -> Toast(id = id, message = localizedMessage, type = ToastType.Success, duration = 4.seconds)
        is AppNotification.Normal -> Toast(id = id, message = localizedMessage, type = ToastType.Normal, duration = 3.seconds)
    }
}

private suspend fun AppNotificationMessage.localizedText(): String = when (this) {
    is AppNotificationMessage.Raw -> value
    is AppNotificationMessage.Localized -> {
        val resource = when (key) {
            AppNotificationMessageKey.ERROR_UNKNOWN -> Res.string.error_unknown
            AppNotificationMessageKey.HTTP_BAD_REQUEST -> Res.string.notification_http_bad_request
            AppNotificationMessageKey.HTTP_NOT_FOUND -> Res.string.notification_http_not_found
            AppNotificationMessageKey.HTTP_CONTENT_TOO_LARGE -> Res.string.notification_http_content_too_large
            AppNotificationMessageKey.HTTP_TEAPOT -> Res.string.notification_http_teapot
            AppNotificationMessageKey.HTTP_ERROR -> Res.string.notification_http_error
            AppNotificationMessageKey.LIBRARY_AUTO_IDENTIFICATION_STARTED -> Res.string.notification_library_auto_identification_started
            AppNotificationMessageKey.LIBRARY_SCAN_STARTED -> Res.string.notification_library_scan_started
            AppNotificationMessageKey.LIBRARY_DEEP_SCAN_STARTED -> Res.string.notification_library_deep_scan_started
            AppNotificationMessageKey.LIBRARY_ANALYSIS_STARTED -> Res.string.notification_library_analysis_started
            AppNotificationMessageKey.LIBRARY_REFRESH_STARTED -> Res.string.notification_library_refresh_started
            AppNotificationMessageKey.LIBRARY_TRASH_STARTED -> Res.string.notification_library_trash_started
            AppNotificationMessageKey.SERIES_ANALYSIS_STARTED -> Res.string.notification_series_analysis_started
            AppNotificationMessageKey.SERIES_METADATA_REFRESH_STARTED -> Res.string.notification_series_metadata_refresh_started
            AppNotificationMessageKey.BOOK_ANALYSIS_STARTED -> Res.string.notification_book_analysis_started
            AppNotificationMessageKey.BOOK_METADATA_REFRESH_STARTED -> Res.string.notification_book_metadata_refresh_started
            AppNotificationMessageKey.COLOR_PRESET_NOT_FOUND -> Res.string.notification_color_preset_not_found
            AppNotificationMessageKey.COLOR_PRESET_ALREADY_EXISTS -> Res.string.notification_color_preset_already_exists
            AppNotificationMessageKey.READER_AT_BEGINNING -> Res.string.notification_reader_at_beginning
            AppNotificationMessageKey.IMAGE_CACHE_CLEARED -> Res.string.notification_image_cache_cleared
            AppNotificationMessageKey.OFFLINE_CACHE_CLEARED -> Res.string.notification_offline_cache_cleared
            AppNotificationMessageKey.DISCORD_WEBHOOKS_MISSING -> Res.string.notification_discord_webhooks_missing
            AppNotificationMessageKey.APPRISE_URLS_MISSING -> Res.string.notification_apprise_urls_missing
            AppNotificationMessageKey.NOTIFICATION_TEMPLATES_SAVED -> Res.string.notification_templates_saved
            AppNotificationMessageKey.SERVER_SETTINGS_UPDATED -> Res.string.notification_server_settings_updated
            AppNotificationMessageKey.ALL_LIBRARIES_SCAN_STARTED -> Res.string.notification_all_libraries_scan_started
            AppNotificationMessageKey.ALL_LIBRARIES_TRASH_EMPTIED -> Res.string.notification_all_libraries_trash_emptied
            AppNotificationMessageKey.NO_TASKS_TO_CANCEL -> Res.string.notification_no_tasks_to_cancel
            AppNotificationMessageKey.TASKS_CANCELLED -> Res.string.notification_tasks_cancelled
        }
        getString(resource, *arguments.toTypedArray())
    }
}
