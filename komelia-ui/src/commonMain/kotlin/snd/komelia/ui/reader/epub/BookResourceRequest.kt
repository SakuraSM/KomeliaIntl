package snd.komelia.ui.reader.epub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import io.ktor.http.decodeURLPart
import snd.komelia.komga.api.KomgaBookApi
import snd.komga.client.book.KomgaBookId
import snd.webview.ResourceLoadResult


private val bookResourceRegex = ".*/api/v1/books/(?<bookId>[^/]+)/resource/(?<resourceName>.*)".toRegex()
private val bookManifestRegex = ".*/api/v1/books/(?<bookId>[^/]+)/manifest.*".toRegex()
private val bookPositionsRegex = ".*/api/v1/books/(?<bookId>[^/]+)/positions.*".toRegex()

suspend fun proxyResourceRequest(
    bookApi: KomgaBookApi,
    urlString: String,
    serverUrl: Flow<String>
): ResourceLoadResult {
    val currentServerUrl = serverUrl.first()
    val resourceMatch = bookResourceRegex.find(urlString)?.groups
    if (resourceMatch != null) {
        val bookId = resourceMatch["bookId"]?.value ?: error("Failed to find bookId $urlString")
        val resourceName = resourceMatch["resourceName"]?.value
            ?.normalizeResourceName()
            ?: error("Failed to find resource name $urlString")
        return ResourceLoadResult(
            data = bookApi.getBookEpubResource(KomgaBookId(bookId), resourceName),
            contentType = resourceName.contentType()
        )
    }
    val manifestMatch = bookManifestRegex.find(urlString)?.groups
    if (manifestMatch != null) {
        val bookId = manifestMatch["bookId"]?.value ?: error("Failed to find bookId $urlString")
        return ResourceLoadResult(
            data = Json.encodeToString(
                bookApi.getWebPubManifest(KomgaBookId(bookId))
            ).encodeToByteArray(),
            contentType = "application/webpub+json"
        )
    }
    val bookPositionsMatch = bookPositionsRegex.find(urlString)?.groups
    if (bookPositionsMatch != null) {
        val bookId = bookPositionsMatch["bookId"]?.value ?: error("Failed to find bookId $urlString")

        return ResourceLoadResult(
            data = Json.encodeToString(
                bookApi.getReadiumPositions(KomgaBookId(bookId))
            ).encodeToByteArray(),
            contentType = "application/json"
        )
    }

    check(urlString.startsWith(currentServerUrl)) { "Requests to external hosts are not allowed $urlString" }
    error("Unsupported resource request $urlString")
}

private fun String.normalizeResourceName(): String {
    return substringBefore('#')
        .substringBefore('?')
        .safeDecodeURLPart()
        .replace('\\', '/')
        .trimStart('/')
}

private fun String.safeDecodeURLPart(): String = runCatching { decodeURLPart() }.getOrDefault(this)

private fun String.contentType(): String? {
    return when (substringAfterLast('.', "").lowercase()) {
        "xhtml", "xml", "opf", "ncx" -> "application/xhtml+xml"
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "svg" -> "image/svg+xml"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "otf" -> "font/otf"
        "ttf" -> "font/ttf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> null
    }
}
