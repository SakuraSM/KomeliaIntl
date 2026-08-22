package snd.komelia.ui.home

internal data class HomeGroupOverflowLayout(
    val visibleGroupIndices: List<Int>,
    val overflowGroupIndices: List<Int>,
)

/**
 * Selects the groups shown between the pinned "All" and optional "More" chips.
 * The active group replaces the last ordinary visible group when it would
 * otherwise overflow. All returned indices preserve their source ordering,
 * except that a promoted active group is appended to the visible prefix.
 */
internal fun calculateHomeGroupOverflowLayout(
    availableWidth: Int,
    allChipWidth: Int,
    moreChipWidth: Int,
    groupWidths: List<Int>,
    activeGroupIndex: Int?,
    spacing: Int,
): HomeGroupOverflowLayout {
    if (groupWidths.isEmpty()) return HomeGroupOverflowLayout(emptyList(), emptyList())

    val safeAvailableWidth = availableWidth.coerceAtLeast(0)
    val safeSpacing = spacing.coerceAtLeast(0)
    val safeAllWidth = allChipWidth.coerceAtLeast(0)
    val safeMoreWidth = moreChipWidth.coerceAtLeast(0)
    val safeGroupWidths = groupWidths.map { it.coerceAtLeast(0) }

    fun occupiedWidth(indices: List<Int>, includeMore: Boolean): Long {
        val itemCount = 1 + indices.size + if (includeMore) 1 else 0
        return safeAllWidth.toLong() +
                indices.sumOf { safeGroupWidths[it].toLong() } +
                (if (includeMore) safeMoreWidth.toLong() else 0L) +
                safeSpacing.toLong() * (itemCount - 1).coerceAtLeast(0)
    }

    val allIndices = safeGroupWidths.indices.toList()
    if (occupiedWidth(allIndices, includeMore = false) <= safeAvailableWidth) {
        return HomeGroupOverflowLayout(allIndices, emptyList())
    }

    val activeIndex = activeGroupIndex?.takeIf { it in safeGroupWidths.indices }
    val effectiveWidths = safeGroupWidths.toMutableList()
    if (activeIndex != null) {
        val promotedCapacity = (
                safeAvailableWidth - safeAllWidth - safeMoreWidth - safeSpacing * 2
                ).coerceAtLeast(0)
        effectiveWidths[activeIndex] = effectiveWidths[activeIndex].coerceAtMost(promotedCapacity)
    }

    fun occupiedOverflowWidth(indices: List<Int>): Long {
        val itemCount = 2 + indices.size
        return safeAllWidth.toLong() +
                indices.sumOf { effectiveWidths[it].toLong() } +
                safeMoreWidth.toLong() +
                safeSpacing.toLong() * (itemCount - 1).coerceAtLeast(0)
    }

    val visible = mutableListOf<Int>()
    for (index in safeGroupWidths.indices) {
        if (occupiedOverflowWidth(visible + index) <= safeAvailableWidth) {
            visible += index
        } else {
            break
        }
    }

    if (activeIndex != null && activeIndex !in visible) {
        while (visible.isNotEmpty() && occupiedOverflowWidth(visible + activeIndex) > safeAvailableWidth) {
            visible.removeLast()
        }
        if (occupiedOverflowWidth(visible + activeIndex) <= safeAvailableWidth) {
            visible += activeIndex
        }
    }

    val visibleSet = visible.toSet()
    return HomeGroupOverflowLayout(
        visibleGroupIndices = visible,
        overflowGroupIndices = safeGroupWidths.indices.filterNot(visibleSet::contains),
    )
}
