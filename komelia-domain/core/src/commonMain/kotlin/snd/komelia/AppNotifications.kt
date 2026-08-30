package snd.komelia

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import snd.komf.client.toKomfErrorResponse
import snd.komga.client.common.toErrorResponse
import snd.komga.client.common.toViolationResponse
import kotlin.time.Clock

class AppNotifications {
    private val notifications: MutableStateFlow<Map<Long, AppNotification>> = MutableStateFlow(emptyMap())

    fun getNotifications(): Flow<Collection<AppNotification>> {
        return notifications.map { it.values }
    }

    fun remove(id: Long) {
        notifications.update { current ->
            current.minus(id)
        }
    }

    fun add(notification: AppNotification) {
        notifications.update { current ->
            current.plus(notification.id to notification)
        }
    }

    fun <R> runCatchingToNotifications(
        coroutineScope: CoroutineScope,
        onFailure: (exception: Throwable) -> Unit = {},
        onSuccess: (value: R) -> Unit = {},
        block: suspend () -> R,
    ) {
        coroutineScope.launch {
            runCatchingToNotifications { block() }
                .onFailure(onFailure)
                .onSuccess(onSuccess)
        }
    }

    suspend inline fun <R> runCatchingToNotifications(block: () -> R): Result<R> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            KotlinLogging.logger {}.warn(e) {}
            throw e
        } catch (exception: Throwable) {
            addErrorNotification(exception)
            return Result.failure(exception)
        }
    }

    suspend fun addErrorNotification(exception: Throwable) {
        KotlinLogging.logger {}.catching(exception)
        when (exception) {
            is CancellationException -> {}
            is ResponseException -> toErrorNotification(exception)
            else -> {
                val message = exception.message ?: exception.cause?.message
                if (message == null) add(AppNotification.Error(AppNotificationMessageKey.ERROR_UNKNOWN))
                else add(AppNotification.Error(message))
            }
        }
    }

    private suspend fun toErrorNotification(exception: ResponseException) {
        val contentType = exception.response.contentType()
        contentType?.toString()
        if (contentType != null && contentType.contentType == "application" && contentType.contentSubtype == "json") {
            add(AppNotification.Error(parseJsonErrorMessage(exception)))
        } else {
            add(AppNotification.Error(errorMessageFromStatusCode(exception.response.status)))
        }
    }
}

private suspend fun parseJsonErrorMessage(exception: ResponseException): String {
    return exception.toErrorResponse()?.message
        ?: exception.toKomfErrorResponse()?.message
        ?: exception.toViolationResponse()
            ?.violations?.firstOrNull()?.let { "${it.fieldName}: ${it.message}" }
        ?: exception.response.bodyAsText()
}

private fun errorMessageFromStatusCode(statusCode: HttpStatusCode): AppNotificationMessage {
    val key = when (statusCode.value) {
        400 -> AppNotificationMessageKey.HTTP_BAD_REQUEST
        404 -> AppNotificationMessageKey.HTTP_NOT_FOUND
        413 -> AppNotificationMessageKey.HTTP_CONTENT_TOO_LARGE
        418 -> AppNotificationMessageKey.HTTP_TEAPOT
        else -> AppNotificationMessageKey.HTTP_ERROR
    }
    return AppNotificationMessage.Localized(key, listOf(statusCode.value.toString()))
}

enum class AppNotificationMessageKey {
    ERROR_UNKNOWN,
    HTTP_BAD_REQUEST,
    HTTP_NOT_FOUND,
    HTTP_CONTENT_TOO_LARGE,
    HTTP_TEAPOT,
    HTTP_ERROR,
    LIBRARY_AUTO_IDENTIFICATION_STARTED,
    LIBRARY_SCAN_STARTED,
    LIBRARY_DEEP_SCAN_STARTED,
    LIBRARY_ANALYSIS_STARTED,
    LIBRARY_REFRESH_STARTED,
    LIBRARY_TRASH_STARTED,
    SERIES_ANALYSIS_STARTED,
    SERIES_METADATA_REFRESH_STARTED,
    BOOK_ANALYSIS_STARTED,
    BOOK_METADATA_REFRESH_STARTED,
    BOOK_MARKED_READ,
    BOOK_MARKED_UNREAD,
    COLOR_PRESET_NOT_FOUND,
    COLOR_PRESET_ALREADY_EXISTS,
    READER_AT_BEGINNING,
    IMAGE_CACHE_CLEARED,
    OFFLINE_CACHE_CLEARED,
    DISCORD_WEBHOOKS_MISSING,
    APPRISE_URLS_MISSING,
    NOTIFICATION_TEMPLATES_SAVED,
    SERVER_SETTINGS_UPDATED,
    ALL_LIBRARIES_SCAN_STARTED,
    ALL_LIBRARIES_TRASH_EMPTIED,
    NO_TASKS_TO_CANCEL,
    TASKS_CANCELLED,
}

sealed interface AppNotificationMessage {
    data class Raw(val value: String) : AppNotificationMessage
    data class Localized(
        val key: AppNotificationMessageKey,
        val arguments: List<String> = emptyList(),
    ) : AppNotificationMessage
}

sealed class AppNotification(val id: Long = Clock.System.now().toEpochMilliseconds()) {
    abstract val message: AppNotificationMessage

    class Success(override val message: AppNotificationMessage) : AppNotification() {
        constructor(message: String) : this(AppNotificationMessage.Raw(message))
        constructor(key: AppNotificationMessageKey, vararg arguments: String) :
            this(AppNotificationMessage.Localized(key, arguments.toList()))
    }

    class Normal(override val message: AppNotificationMessage) : AppNotification() {
        constructor(message: String) : this(AppNotificationMessage.Raw(message))
        constructor(key: AppNotificationMessageKey, vararg arguments: String) :
            this(AppNotificationMessage.Localized(key, arguments.toList()))
    }

    class Error(override val message: AppNotificationMessage) : AppNotification() {
        constructor(message: String?) : this(
            message?.let(AppNotificationMessage::Raw)
                ?: AppNotificationMessage.Localized(AppNotificationMessageKey.ERROR_UNKNOWN)
        )
        constructor(key: AppNotificationMessageKey, vararg arguments: String) :
            this(AppNotificationMessage.Localized(key, arguments.toList()))
    }
}
