package snd.komelia.offline.mediacontainer

enum class LocalArchiveFailure { COPY_LIMIT, LOW_SPACE }

class LocalArchiveAccessException(val reason: LocalArchiveFailure) : IllegalStateException(
    when (reason) {
        LocalArchiveFailure.COPY_LIMIT -> "Archive temporary-copy limit reached. Move the book to a device folder that supports direct file access."
        LocalArchiveFailure.LOW_SPACE -> "Not enough space for a temporary archive copy. Free device storage or move the book to a folder that supports direct file access."
    }
)
