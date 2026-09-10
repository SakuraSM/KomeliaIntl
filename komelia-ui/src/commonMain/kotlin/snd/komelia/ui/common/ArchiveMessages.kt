package snd.komelia.ui.common

import androidx.compose.runtime.Composable
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_copy_limit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_low_space
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_unknown_format
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_corrupt
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_unsupported_codec
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_encrypted
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_multi_volume
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_permission
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_source_io
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_memory_limit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_decode_limit
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_archive_unsafe_entry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import snd.komelia.offline.mediacontainer.LocalArchiveAccessException
import snd.komelia.offline.mediacontainer.LocalArchiveFailure

internal val ARCHIVE_FAILURE_MESSAGES: Map<LocalArchiveFailure, StringResource> = mapOf(
    LocalArchiveFailure.COPY_LIMIT to Res.string.local_archive_copy_limit,
    LocalArchiveFailure.LOW_SPACE to Res.string.local_archive_low_space,
    LocalArchiveFailure.UNKNOWN_FORMAT to Res.string.local_archive_unknown_format,
    LocalArchiveFailure.CORRUPT to Res.string.local_archive_corrupt,
    LocalArchiveFailure.UNSUPPORTED_CODEC to Res.string.local_archive_unsupported_codec,
    LocalArchiveFailure.ENCRYPTED to Res.string.local_archive_encrypted,
    LocalArchiveFailure.MULTI_VOLUME to Res.string.local_archive_multi_volume,
    LocalArchiveFailure.PERMISSION to Res.string.local_archive_permission,
    LocalArchiveFailure.SOURCE_IO to Res.string.local_archive_source_io,
    LocalArchiveFailure.MEMORY_LIMIT to Res.string.local_archive_memory_limit,
    LocalArchiveFailure.DECODE_LIMIT to Res.string.local_archive_decode_limit,
    LocalArchiveFailure.UNSAFE_ENTRY to Res.string.local_archive_unsafe_entry,
)

@Composable
fun archiveFailureMessage(reason: LocalArchiveFailure): String = stringResource(ARCHIVE_FAILURE_MESSAGES.getValue(reason))

@Composable
fun archiveErrorMessage(error: Throwable): String {
    val archiveError = generateSequence(error) { it.cause }.take(20).filterIsInstance<LocalArchiveAccessException>().firstOrNull()
    return archiveError?.let { archiveFailureMessage(it.reason) } ?: "${error::class.simpleName}: ${error.message}"
}
