package snd.komelia.offline.mediacontainer

enum class LocalArchiveFailure {
    COPY_LIMIT, LOW_SPACE, UNKNOWN_FORMAT, CORRUPT, UNSUPPORTED_CODEC,
    ENCRYPTED, MULTI_VOLUME, PERMISSION, SOURCE_IO, MEMORY_LIMIT, DECODE_LIMIT, UNSAFE_ENTRY,
}

class LocalArchiveAccessException(val reason: LocalArchiveFailure, cause: Throwable? = null) : IllegalStateException(
    when (reason) {
        LocalArchiveFailure.COPY_LIMIT -> "Archive cache limit reached: 1 GiB per file and 2 GiB total. Use a smaller archive or close other readers and retry."
        LocalArchiveFailure.LOW_SPACE -> "Not enough space to prepare the archive cache. Free device storage and retry."
        LocalArchiveFailure.UNKNOWN_FORMAT -> "The file is not a recognized ZIP, RAR or 7z archive. Changing its extension does not convert its format."
        LocalArchiveFailure.CORRUPT -> "The archive is incomplete or damaged. Check the original file or download it again."
        LocalArchiveFailure.UNSUPPORTED_CODEC -> "This archive uses an unsupported compression method or RAR version. Repack it as ZIP or a supported 7z archive."
        LocalArchiveFailure.ENCRYPTED -> "Password-protected archives are not supported. Create an unencrypted copy."
        LocalArchiveFailure.MULTI_VOLUME -> "Multi-volume archives are not supported. Combine and repack the complete archive first."
        LocalArchiveFailure.PERMISSION -> "Archive access was denied. Grant access to the source folder again."
        LocalArchiveFailure.SOURCE_IO -> "The archive source could not be read. Check that the file is available and the folder permission is still valid."
        LocalArchiveFailure.MEMORY_LIMIT -> "The 7z archive exceeds the 128 MiB decoder memory limit. Repack it with a smaller dictionary or as ZIP."
        LocalArchiveFailure.DECODE_LIMIT -> "The 7z archive exceeds the decoding limits: 64 MiB per entry, 1 GiB total or 10,000 entries. Split or repack it."
        LocalArchiveFailure.UNSAFE_ENTRY -> "The archive contains unsafe or duplicate entry paths. Repack it with unique relative paths."
    }, cause,
)
