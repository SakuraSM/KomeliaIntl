package snd.webview

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Maps EPUB resources to the private loopback endpoint of one macOS WebView. */
internal class MacResourceUrlMapper(private val sessionId: String, private val port: Int) {
    private val base = "http://127.0.0.1:$port"
    private val origin = "$base/$sessionId"
    private val markupTokens = Regex(
        """(?is)<!--.*?-->|<!\[CDATA\[.*?]]>|<script\b(?:[^>'\"]|'[^']*'|\"[^\"]*\")*>.*?</script\s*>|<style\b(?:[^>'\"]|'[^']*'|\"[^\"]*\")*>.*?</style\s*>|<(?:[^>'\"]|'[^']*'|\"[^\"]*\")*>"""
    )

    fun toWebUrl(url: String, serverBase: Boolean = false): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        if (uri.scheme != "http" && uri.scheme != "https") return url
        val path = uri.rawPath.orEmpty()
        if (!serverBase && uri.host != "komelia" && !path.contains("/api/v1/books/")) return url
        return origin + path + (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
    }

    fun toOriginalUrl(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val path = uri.rawPath.orEmpty()
        if (uri.scheme != "http" || uri.host != "127.0.0.1" || uri.port != port ||
            uri.rawUserInfo != null || !path.startsWith("/$sessionId/")
        ) return null
        return "http://komelia" + path.removePrefix("/$sessionId") + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    fun rewriteResource(contentType: String?, data: ByteArray): ByteArray {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return data
        val transform: (String) -> String = when (type) {
            "text/css" -> ::rewriteCss
            "text/html", "application/xhtml+xml", "image/svg+xml" -> ::rewriteMarkup
            "application/json", "application/webpub+json" -> ::rewriteTextResource
            else -> return data
        }
        return runCatching {
            val prefix = data.take(512).toByteArray().toString(Charsets.ISO_8859_1)
            val declared = Regex("""(?i)(?:encoding\s*=\s*[\"']|@charset\s+[\"']|charset\s*=\s*)([\w.-]+)""")
                .find(contentType + " " + prefix)?.groupValues?.get(1)
            val charset = when {
                data.size >= 2 && ((data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) ||
                    (data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte())) -> Charsets.UTF_16
                declared != null -> Charset.forName(declared)
                else -> Charsets.UTF_8
            }
            val text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString()
            val rewritten = transform(text)
            if (rewritten == text) data else rewritten.toByteArray(charset)
        }.getOrDefault(data)
    }

    fun rewriteMarkup(text: String): String {
        return markupTokens.replace(text) { match ->
            val token = match.value
            when {
                token.startsWith("<!--") || token.startsWith("<![CDATA[") || token.startsWith("<script", true) -> token
                token.startsWith("<style", true) -> {
                    val openEnd = tagEnd(token) + 1
                    val closeStart = token.lastIndexOf("</style", ignoreCase = true)
                    if (openEnd > 0 && closeStart >= openEnd) {
                        rewriteTag(token.substring(0, openEnd)) + rewriteCss(token.substring(openEnd, closeStart)) + token.substring(closeStart)
                    } else token
                }
                else -> rewriteTag(token)
            }
        }
    }

    fun rewriteCss(text: String): String = transformCssUrls(text, ::toWebResourceUrl)

    private fun toWebResourceUrl(url: String) = toWebUrl(url)

    private fun rewriteTag(tag: String): String {
        var index = 1
        while (index < tag.length && !tag[index].isWhitespace() && tag[index] != '>') index++
        var copied = 0
        val result = StringBuilder(tag.length)
        while (index < tag.length) {
            while (index < tag.length && (tag[index].isWhitespace() || tag[index] == '/')) index++
            val nameStart = index
            while (index < tag.length && !tag[index].isWhitespace() && tag[index] !in "=/>" ) index++
            if (nameStart == index) break
            val name = tag.substring(nameStart, index).lowercase()
            while (index < tag.length && tag[index].isWhitespace()) index++
            if (index >= tag.length || tag[index] != '=') continue
            index++
            while (index < tag.length && tag[index].isWhitespace()) index++
            if (index >= tag.length) break
            val quote = tag[index].takeIf { it == '\'' || it == '"' }
            if (quote != null) index++
            val valueStart = index
            if (quote != null) while (index < tag.length && tag[index] != quote) index++
            else while (index < tag.length && !tag[index].isWhitespace() && tag[index] != '>') index++
            val value = tag.substring(valueStart, index)
            val replacement = when (name) {
                "src", "href", "xlink:href", "poster" -> toWebUrl(value)
                "style" -> rewriteCss(value)
                else -> value
            }
            if (replacement != value) {
                result.append(tag, copied, valueStart).append(replacement)
                copied = index
            }
            if (quote != null && index < tag.length) index++
        }
        return result.append(tag, copied, tag.length).toString()
    }

    private fun tagEnd(tag: String): Int {
        var quote: Char? = null
        for (index in tag.indices) {
            val c = tag[index]
            if (quote != null && c == quote) quote = null
            else if (quote == null && (c == '\'' || c == '"')) quote = c
            else if (quote == null && c == '>') return index
        }
        return -1
    }

    fun rewriteResult(name: String, response: String, request: String = "[]"): String {
        val root = runCatching { Json.parseToJsonElement(response) as JsonObject }.getOrNull() ?: return response
        val result = root["result"] ?: return response
        val rewritten = when (name) {
            "getServerUrl" -> result.rewriteString { toWebUrl(it, serverBase = true) }
            "getPublication", "bookGetProgression" -> rewriteHrefs(result)
            "getBookData" -> rewriteBookData(result)
            "d2ReaderGetContent", "externalFetch" -> result.rewriteString { text ->
                val href = runCatching { (Json.parseToJsonElement(request) as JsonArray)[0] as JsonPrimitive }
                    .getOrNull()?.contentOrNull.orEmpty().substringBefore('?').substringBefore('#')
                if (href.endsWith(".css", ignoreCase = true)) rewriteCss(text) else rewriteTextResource(text)
            }
            else -> result
        }
        return JsonObject(root + ("result" to rewritten)).toString()
    }

    private fun rewriteBookData(value: JsonElement): JsonElement {
        val book = value as? JsonObject ?: return value
        return JsonObject(book.mapValues { (key, item) ->
            when (key) {
                "elementHtml" -> item.rewriteString(::rewriteMarkup)
                "styleSheet" -> item.rewriteString(::rewriteCss)
                "imageUrls" -> if (item is JsonArray) JsonArray(item.map { it.rewriteString { url -> toWebUrl(url) } }) else item
                else -> item
            }
        })
    }

    private fun rewriteTextResource(text: String): String {
        val value = runCatching { Json.parseToJsonElement(text) }.getOrNull()
        return if (value is JsonObject || value is JsonArray) rewriteHrefs(value).toString() else rewriteMarkup(text)
    }

    private fun rewriteHrefs(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, item) ->
            if (key == "href") item.rewriteString { url -> toWebUrl(url) } else rewriteHrefs(item)
        })
        is JsonArray -> JsonArray(value.map(::rewriteHrefs))
        else -> value
    }

    private fun JsonElement.rewriteString(transform: (String) -> String): JsonElement {
        val value = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return this
        return JsonPrimitive(transform(value))
    }
}
