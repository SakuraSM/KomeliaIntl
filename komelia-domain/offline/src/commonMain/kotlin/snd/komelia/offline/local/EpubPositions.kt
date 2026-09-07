package snd.komelia.offline.local

import snd.komga.client.book.R2Location
import snd.komga.client.book.R2Locator
import snd.komga.client.book.WPLink

internal const val LOCAL_EPUB_INSPECTION_VERSION = 1
private const val BYTES_PER_POSITION = 1024

internal fun buildLocalEpubPositions(readingOrder: List<WPLink>, readEntry: (String) -> ByteArray): List<R2Locator> {
    // Generated during background library inspection, never on the reader's first-paint path.
    val sizes = readingOrder.map { readEntry(requireNotNull(it.href)).size.coerceAtLeast(1).toLong() }
    val totalSize = sizes.sum().toDouble()
    var precedingSize = 0L
    var position = 0
    return readingOrder.flatMapIndexed { index, link ->
        val size = sizes[index]
        val intervals = ((size + BYTES_PER_POSITION - 1) / BYTES_PER_POSITION).toInt().coerceAtLeast(1)
        val positions = (0..intervals).map { interval ->
            val progression = interval.toFloat() / intervals
            R2Locator(
                href = requireNotNull(link.href), type = link.type ?: "application/xhtml+xml", title = link.title,
                locations = R2Location(
                    position = ++position, progression = progression,
                    totalProgression = ((precedingSize + size * progression.toDouble()) / totalSize).toFloat(),
                ),
            )
        }
        precedingSize += size
        positions
    }
}
