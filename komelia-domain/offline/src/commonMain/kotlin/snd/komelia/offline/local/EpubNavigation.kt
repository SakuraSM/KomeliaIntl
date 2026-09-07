package snd.komelia.offline.local

import snd.komelia.offline.media.model.EpubTocEntry
import snd.komga.client.book.WPLink

internal data class EpubNavigation(
    val toc: List<EpubTocEntry> = emptyList(),
    val landmarks: List<EpubTocEntry> = emptyList(),
    val pageList: List<EpubTocEntry> = emptyList(),
)

internal fun EpubTocEntry.toPublicationLink(): WPLink = WPLink(
    title = title, href = href, children = children.map { it.toPublicationLink() },
)

internal fun parseEpubNavigation(xml: String, path: String): EpubNavigation {
    val root = parseNavigationXml(xml)
    val directory = path.substringBeforeLast('/', "")
    fun section(type: String): List<EpubTocEntry> = root.descendants("nav")
        .firstOrNull { type in it.attributes["type"].orEmpty().split(Regex("\\s+")) }
        ?.children?.firstOrNull { it.name == "ol" }?.let { parseNavigationList(it, directory) }.orEmpty()
    val ncx = root.descendants("navmap").firstOrNull()
    return EpubNavigation(
        toc = section("toc").ifEmpty { ncx?.let { parseNcxPoints(it, directory) }.orEmpty() },
        landmarks = section("landmarks"),
        pageList = section("page-list"),
    )
}

private fun parseNavigationList(list: NavigationNode, directory: String): List<EpubTocEntry> =
    list.children.filter { it.name == "li" }.mapNotNull { item ->
        val label = item.children.firstOrNull { it.name == "a" || it.name == "span" }
        val children = item.children.firstOrNull { it.name == "ol" }
            ?.let { parseNavigationList(it, directory) }.orEmpty()
        if (label == null && children.isEmpty()) return@mapNotNull null
        EpubTocEntry(
            title = label?.text?.toString()?.trim().orEmpty(),
            href = label?.attributes?.get("href")?.let { resolveNavigationHref(directory, it) },
            children = children,
        )
    }

private fun parseNcxPoints(parent: NavigationNode, directory: String): List<EpubTocEntry> =
    parent.children.filter { it.name == "navpoint" }.map { point ->
        EpubTocEntry(
            title = point.children.firstOrNull { it.name == "navlabel" }?.text?.toString()?.trim().orEmpty(),
            href = point.children.firstOrNull { it.name == "content" }?.attributes?.get("src")
                ?.let { resolveNavigationHref(directory, it) },
            children = parseNcxPoints(point, directory),
        )
    }

private fun resolveNavigationHref(directory: String, href: String): String? {
    // Navigation must remain inside the archive; do not turn scripts or external URLs into reader links.
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(href) || href.startsWith("//")) return null
    return resolveArchivePath(directory, href)
}

private class NavigationNode(
    val name: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    val children = mutableListOf<NavigationNode>()
    val text = StringBuilder()
    fun descendants(name: String): List<NavigationNode> = children.flatMap {
        (if (it.name == name) listOf(it) else emptyList()) + it.descendants(name)
    }
}

// Navigation only needs element hierarchy, attributes and text. No DTD/entity expansion or I/O is performed.
// Bound nesting before recursive TOC conversion, including malformed or adversarial publications.
private const val MAX_NAVIGATION_DEPTH = 64
private fun parseNavigationXml(xml: String): NavigationNode {
    val root = NavigationNode("root")
    val stack = mutableListOf(root)
    val tokens = Regex("<!--.*?-->|<!\\[CDATA\\[.*?]]>|<[^>\"']*(?:\"[^\"]*\"[^>\"']*|'[^']*'[^>\"']*)*>|[^<]+", RegexOption.DOT_MATCHES_ALL)
    val attributes = Regex("([\\w:.-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.DOT_MATCHES_ALL)
    tokens.findAll(xml).forEach { match ->
        val token = match.value
        when {
            token.startsWith("<![CDATA[") -> stack.forEach { it.text.append(token.removePrefix("<![CDATA[").removeSuffix("]]>")) }
            token.startsWith("<!") || token.startsWith("<?") -> Unit
            token.startsWith("</") -> {
                val name = token.drop(2).substringBefore('>').trim().substringAfter(':').lowercase()
                val index = stack.indexOfLast { it.name == name }
                if (index > 0) while (stack.size > index) stack.removeLast()
            }
            token.startsWith('<') -> {
                val name = token.drop(1).takeWhile { !it.isWhitespace() && it != '/' && it != '>' }
                    .substringAfter(':').lowercase()
                val node = NavigationNode(name, attributes.findAll(token).associate {
                    it.groupValues[1].substringAfter(':').lowercase() to it.groupValues[3].decodeXmlEntities()
                })
                stack.last().children += node
                if (!token.endsWith("/>")) {
                    require(stack.size < MAX_NAVIGATION_DEPTH) { "EPUB navigation nesting exceeds limit" }
                    stack += node
                }
            }
            else -> stack.forEach { it.text.append(token.decodeXmlEntities()) }
        }
    }
    return root
}
