package snd.komelia.offline.mediacontainer

import java.io.File

internal fun desktopComicArchives(): ComicArchiveService =
    ComicArchiveService.forDirectory(desktopComicArchiveCacheDirectory())

internal fun desktopComicArchiveCacheDirectory(): File =
    File(System.getProperty("java.io.tmpdir"), "komelia/comic-archives").canonicalFile
