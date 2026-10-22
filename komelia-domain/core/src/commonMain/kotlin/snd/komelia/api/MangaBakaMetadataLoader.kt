package snd.komelia.api

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import snd.komelia.AppNotification
import snd.komelia.AppNotificationMessageKey
import snd.komelia.AppNotifications

internal const val MANGABAKA_METADATA_TIMEOUT_MILLIS = 3_000L

/** Optional enrichment must not discard an already loaded Komga catalog. */
class MangaBakaMetadataLoader(private val notifications: AppNotifications) {
    private val hasReportedFailure = MutableStateFlow(false)

    suspend fun <Metadata> load(fallback: Metadata, request: suspend () -> Metadata): Metadata {
        return try {
            val response = withTimeoutOrNull(MANGABAKA_METADATA_TIMEOUT_MILLIS) {
                MetadataResponse(request())
            }
            if (response == null) {
                reportUnavailable()
                fallback
            } else {
                hasReportedFailure.value = false
                response.value
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            KotlinLogging.logger {}.warn { "MangaBaka enrichment unavailable: ${error::class.simpleName}" }
            reportUnavailable()
            fallback
        }
    }

    private fun reportUnavailable() {
        if (hasReportedFailure.compareAndSet(false, true)) {
            notifications.add(AppNotification.Normal(AppNotificationMessageKey.MANGABAKA_METADATA_UNAVAILABLE))
        }
    }

    private data class MetadataResponse<Metadata>(val value: Metadata)
}
