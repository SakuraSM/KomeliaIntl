package snd.komelia.ui.reader.epub

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import snd.komga.client.book.R2Device
import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.R2Progression
import kotlin.time.Clock
import kotlin.time.Instant

/** An initial locator passed to the reader; selecting it does not write server progress. */
internal fun chapterStartProgression(href: String, previous: R2Progression?, now: Instant = Clock.System.now()): R2Progression {
    val resource = epubResourceName(href)
    val fragment = href.substringAfter('#', "").takeIf { it.isNotEmpty() }
    val locator = R2Locator(href = resource.substringBefore('#'), type = "application/xhtml+xml",
        locations = R2Location(fragment = fragment?.let(::listOf), progression = 0f))
    return if (previous == null) R2Progression(modified = now, device = R2Device("unused", "Komelia"), locator = locator)
        else previous.copy(modified = now, locator = locator)
}

/** Preserve the reader's own character-count rules when locating an anchor inside a chapter. */
internal fun epubPrefixBeforeElement(root: Element, target: Element): Element {
    val prefix = Element("div")
    fun copyBefore(node: Node, destination: Element): Boolean {
        if (node === target) return true
        if (node is Element) {
            val copy = Element(node.tagName())
            node.attributes().forEach { copy.attr(it.key, it.value) }
            destination.appendChild(copy)
            for (child in node.childNodes()) if (copyBefore(child, copy)) return true
        } else destination.appendChild(node.clone())
        return false
    }
    for (child in root.childNodes()) if (copyBefore(child, prefix)) break
    return prefix
}
