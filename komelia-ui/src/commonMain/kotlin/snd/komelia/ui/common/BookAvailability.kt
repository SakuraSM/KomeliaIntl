package snd.komelia.ui.common

import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.local.isLocalLibrary

/** A remote-source warning does not make an existing local copy unavailable. */
internal fun bookContentIsAvailable(book: KomeliaBook, libraryUnavailable: Boolean): Boolean =
    book.downloaded || book.libraryId.isLocalLibrary() || (!book.deleted && !book.remoteFileUnavailable && !libraryUnavailable)
