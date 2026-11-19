package snd.komelia.ui.reader.epub

import io.ktor.http.Url
import snd.komelia.resolve
import snd.webview.transformCssUrls

private val urlScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

internal fun resolveStylesheetUrls(css: String, stylesheetUrl: String): String {
    val base = Url(stylesheetUrl)
    return transformCssUrls(css) { url ->
        if (url.startsWith('/') || url.startsWith('#') || urlScheme.containsMatchIn(url) || '\\' in url) url
        else runCatching { base.resolve(url).toString() }.getOrDefault(url)
    }
}
