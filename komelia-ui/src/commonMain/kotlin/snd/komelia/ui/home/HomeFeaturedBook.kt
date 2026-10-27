package snd.komelia.ui.home

import snd.komelia.komga.api.model.KomeliaBook

internal data class HomeFeaturedBook(val groupOrder: Int, val book: KomeliaBook)

/** Uses only visible configured groups; it never creates a new catalog query or reorders books. */
internal fun homeFeaturedBook(filters: List<HomeFilterData>, activeGroup: Int): HomeFeaturedBook? {
    for (group in filters.sortedBy { it.filter.order }) {
        if (activeGroup != HOME_ALL_TAB_ID && group.filter.order != activeGroup) continue
        if (group !is BookFilterData) continue
        val book = group.books.firstOrNull {
            val progress = it.readProgress
            !it.deleted && it.media.pagesCount > 0 && progress != null && !progress.completed && progress.page > 0
        } ?: continue
        return HomeFeaturedBook(group.filter.order, book)
    }
    return null
}
