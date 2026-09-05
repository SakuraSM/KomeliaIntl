package snd.komelia.ui.search

internal const val SEARCH_PAGE_SIZE = 10

enum class SearchCoverage {
    COMPLETE,
    REMOTE_ONLY,
    OFFLINE_ONLY,
}

internal data class SearchSourcePage<T>(
    val content: List<T>,
    val totalElements: Int,
)

internal data class UnifiedSearchPage<T>(
    val content: List<T>,
    val currentPage: Int,
    val totalPages: Int,
)

internal data class SearchLoadResult<T>(
    val page: UnifiedSearchPage<T>,
    val coverage: SearchCoverage,
)

internal fun searchFetchSize(pageNumber: Int, pageSize: Int = SEARCH_PAGE_SIZE): Int =
    pageNumber.coerceAtLeast(1) * pageSize

internal fun <T, K> mergeSearchPages(
    remote: SearchSourcePage<T>,
    local: SearchSourcePage<T>,
    pageNumber: Int,
    pageSize: Int = SEARCH_PAGE_SIZE,
    idOf: (T) -> K,
    comparator: Comparator<T>,
): UnifiedSearchPage<T> {
    val safePage = pageNumber.coerceAtLeast(1)
    val duplicateIds = remote.content.map(idOf).toSet().intersect(local.content.map(idOf).toSet()).size
    val totalElements = (remote.totalElements + local.totalElements - duplicateIds).coerceAtLeast(0)
    val totalPages = ((totalElements + pageSize - 1) / pageSize).coerceAtLeast(1)
    val currentPage = safePage.coerceAtMost(totalPages)
    val offset = (currentPage - 1) * pageSize
    val content = (remote.content + local.content)
        .distinctBy(idOf)
        .sortedWith(comparator)
        .drop(offset)
        .take(pageSize)

    return UnifiedSearchPage(
        content = content,
        currentPage = currentPage,
        totalPages = totalPages,
    )
}
