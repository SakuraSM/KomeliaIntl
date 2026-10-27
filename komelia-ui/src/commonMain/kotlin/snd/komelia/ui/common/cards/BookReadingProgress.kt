package snd.komelia.ui.common.cards

import snd.komelia.komga.api.model.KomeliaBook

internal fun bookReadProgressFraction(book: KomeliaBook): Float? {
    val progress = book.readProgress ?: return 0f
    if (progress.completed) return 1f
    if (book.media.pagesCount <= 0) return null
    return (progress.page.toFloat() / book.media.pagesCount).coerceIn(0f, 1f)
}
