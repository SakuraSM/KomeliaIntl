package snd.komelia.ui.reader.epub

import kotlinx.serialization.Serializable
import snd.komga.client.book.WPLink

@Serializable
data class TtuTocEntry(
    val title: String?,
    val reference: String?,
    val children: List<TtuTocEntry> = emptyList(),
)

internal fun ttuNavigationEntries(toc: List<WPLink>, readingOrder: List<WPLink>): List<TtuTocEntry> {
    val resources = readingOrder.mapNotNull { it.href }.associateBy(::epubResourceName)

    fun convert(entry: WPLink): TtuTocEntry {
        val href = entry.href?.takeIf { it.isNotBlank() }
        val reference = href?.let { value ->
            resources[epubResourceName(value)]?.let { resource ->
                val fragment = value.substringAfter('#', "")
                resource + if (fragment.isEmpty()) "" else "#$fragment"
            }
        }
        return TtuTocEntry(entry.title, reference, entry.children.map(::convert))
    }

    return toc.map(::convert)
}
