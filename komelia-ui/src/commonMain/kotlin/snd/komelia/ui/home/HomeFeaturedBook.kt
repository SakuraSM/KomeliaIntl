package snd.komelia.ui.home

import snd.komelia.komga.api.model.KomeliaBook

internal data class HomeFeaturedBook(val groupOrder: Int, val book: KomeliaBook)

/** Selects recent progress without changing a configured group's order or membership. */
internal fun homeFeaturedBook(filters: List<HomeFilterData>, activeGroup: Int): HomeFeaturedBook? {
    return filters.sortedBy { it.filter.order }
        .filterIsInstance<BookFilterData>()
        .filter { activeGroup == HOME_ALL_TAB_ID || it.filter.order == activeGroup }
        .flatMap { group -> group.books.filter(::canContinueReading).map { HomeFeaturedBook(group.filter.order, it) } }
        .minWithOrNull(compareByDescending { it.book.readProgress?.readDate })
}

internal fun mostRecentlyReadingBook(books: List<KomeliaBook>): KomeliaBook? =
    books.filter(::canContinueReading).minWithOrNull(compareByDescending { it.readProgress?.readDate })

private fun canContinueReading(book: KomeliaBook): Boolean =
    !book.deleted && book.readProgress?.let { !it.completed && it.page > 0 } == true
