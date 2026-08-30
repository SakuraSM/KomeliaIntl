package snd.komelia.offline.local

import snd.komelia.offline.media.model.EpubTocEntry
import snd.komelia.offline.media.model.MediaExtensionEpub
import snd.komelia.offline.media.model.OfflineBookPage
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.MediaProfile
import snd.komga.client.book.WPLink
import snd.komga.client.book.WPMetadata
import snd.komga.client.book.WPPublication

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")

internal fun isSupportedLocalBook(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("cbz", "zip", "cbr", "rar", "pdf", "epub")

internal fun WPPublication.withLocalBookResourceUrls(bookId: KomgaBookId): WPPublication {
    val resourceBase = "local://device/api/v1/books/${bookId.value}/resource/"

    fun WPLink.withResourceUrl(): WPLink = copy(
        href = href?.let { value ->
            value.takeIf { "://" in it } ?: "$resourceBase${value.trimStart('/')}"
        },
        alternate = alternate.map { it.withResourceUrl() },
        children = children.map { it.withResourceUrl() },
    )

    return copy(
        links = links.map { it.withResourceUrl() },
        images = images.map { it.withResourceUrl() },
        readingOrder = readingOrder.map { it.withResourceUrl() },
        resources = resources.map { it.withResourceUrl() },
        toc = toc.map { it.withResourceUrl() },
        landmarks = landmarks.map { it.withResourceUrl() },
        pageList = pageList.map { it.withResourceUrl() },
    )
}

internal fun inspectComicArchive(
    entries: List<Pair<String, Long?>>,
    readEntry: (String) -> ByteArray,
    mediaType: String,
): LocalBookInspection {
    val imageEntries = entries
        .filter { (name, _) -> name.substringAfterLast('.', "").lowercase() in imageExtensions }
        .sortedBy { naturalArchiveKey(it.first) }
    require(imageEntries.isNotEmpty()) { "Archive contains no supported images" }

    val pages = imageEntries.map { (name, size) ->
        OfflineBookPage(
            bookId = KomgaBookId(""),
            fileName = name,
            mediaType = imageMediaType(name),
            width = null,
            height = null,
            fileSize = size,
        )
    }
    return LocalBookInspection(
        mediaType = mediaType,
        mediaProfile = MediaProfile.DIVINA,
        pages = pages,
        thumbnail = readEntry(imageEntries.first().first),
    )
}

internal fun inspectEpubArchive(
    entries: List<String>,
    readEntry: (String) -> ByteArray,
): LocalBookInspection {
    val container = readEntry("META-INF/container.xml").decodeToString()
    val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        .find(container)?.groupValues?.get(1)
        ?: error("EPUB package path is missing")
    val opf = readEntry(opfPath).decodeToString()
    val opfDirectory = opfPath.substringBeforeLast('/', "")

    data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
    val manifest = Regex("<item\\b([^>]+?)/?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(opf)
        .mapNotNull { match ->
            val attributes = match.groupValues[1]
            val id = attribute(attributes, "id") ?: return@mapNotNull null
            val href = attribute(attributes, "href") ?: return@mapNotNull null
            ManifestItem(
                id = id,
                href = resolveArchivePath(opfDirectory, href),
                mediaType = attribute(attributes, "media-type") ?: "application/octet-stream",
                properties = attribute(attributes, "properties") ?: "",
            )
        }
        .associateBy { it.id }
    val spineIds = Regex("<itemref\\b([^>]+?)/?>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(opf)
        .mapNotNull { attribute(it.groupValues[1], "idref") }
        .toList()
    val readingOrder = spineIds.mapNotNull(manifest::get).map { item ->
        WPLink(href = item.href, type = item.mediaType)
    }
    require(readingOrder.isNotEmpty()) { "EPUB reading order is empty" }

    val title = xmlText(opf, "dc:title") ?: xmlText(opf, "title") ?: "EPUB"
    val identifier = xmlText(opf, "dc:identifier") ?: stableId(opfPath + title)
    val language = xmlText(opf, "dc:language") ?: ""
    val resources = manifest.values
        .filterNot { item -> item.id in spineIds }
        .map { item -> WPLink(href = item.href, type = item.mediaType) }
    val coverItem = manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') }
        ?: manifest[Regex("<meta[^>]+name=[\"']cover[\"'][^>]+content=[\"']([^\"']+)", RegexOption.IGNORE_CASE)
            .find(opf)?.groupValues?.get(1)]
        ?: manifest.values.firstOrNull { it.id.equals("cover", ignoreCase = true) }
    val cover = coverItem?.href?.takeIf(entries::contains)?.let(readEntry)

    val publication = WPPublication(
        links = emptyList(),
        metadata = WPMetadata(
            title = title,
            identifier = identifier,
            language = language,
            type = "http://schema.org/Book",
        ),
        readingOrder = readingOrder,
        resources = resources,
    )
    return LocalBookInspection(
        mediaType = "application/epub+zip",
        mediaProfile = MediaProfile.EPUB,
        pages = emptyList(),
        extension = MediaExtensionEpub(
            toc = emptyList<EpubTocEntry>(),
            isFixedLayout = false,
            manifest = publication,
        ),
        epubDivinaCompatible = false,
        thumbnail = cover,
    )
}

private fun attribute(attributes: String, name: String): String? =
    Regex("(?:^|\\s)${Regex.escape(name)}\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        .find(attributes)?.groupValues?.get(1)

private fun xmlText(xml: String, tag: String): String? =
    Regex("<${Regex.escape(tag)}(?:\\s[^>]*)?>(.*?)</${Regex.escape(tag)}>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(xml)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()

private fun resolveArchivePath(parent: String, child: String): String {
    val parts = (if (parent.isBlank()) child else "$parent/$child").replace('\\', '/').split('/')
    val resolved = mutableListOf<String>()
    parts.forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (resolved.isNotEmpty()) resolved.removeLast()
            else -> resolved += part
        }
    }
    return resolved.joinToString("/")
}

private fun imageMediaType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "avif" -> "image/avif"
    "bmp" -> "image/bmp"
    else -> "application/octet-stream"
}

private fun naturalArchiveKey(value: String): String = buildString {
    Regex("\\d+|\\D+").findAll(value.lowercase()).forEach { part ->
        val token = part.value
        if (token.firstOrNull()?.isDigit() == true) append(token.padStart(16, '0')) else append(token)
    }
}
