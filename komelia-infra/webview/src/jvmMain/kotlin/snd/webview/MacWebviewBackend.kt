package snd.webview

import ca.weblite.webview.AsyncJavascriptFunction
import ca.weblite.webview.swing.WebViewComponent
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import java.net.InetSocketAddress
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class MacWebviewBackend {
    private data class Pending(val name: String, val request: String, val result: CompletableFuture<String>)

    private val id = UUID.randomUUID().toString().replace("-", "")
    private val cookieName = "komelia_$id"
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val serverExecutor = Executors.newCachedThreadPool { action ->
        Thread(action, "komelia-mac-reader").apply { isDaemon = true }
    }
    private val port = server.address.port
    private val mapper = MacResourceUrlMapper(id, port)
    private val nextCall = AtomicLong()
    private val pending = ConcurrentHashMap<String, Pending>()
    @Volatile private var interceptor: RequestInterceptor? = null
    val component: WebViewComponent = WebViewComponent.create()

    init {
        server.createContext("/") { exchange ->
            try {
                serve(exchange)
            } finally {
                exchange.close()
            }
        }
        server.executor = serverExecutor
        server.start()
        component.setNavigationHandler { event ->
            mapper.toOriginalUrl(event.url()) != null || event.url() == "about:blank" || event.url() == "about:srcdoc"
        }
    }

    private fun serve(exchange: HttpExchange) {
        val host = "127.0.0.1:$port"
        if (exchange.requestMethod != "GET" || exchange.requestHeaders.getFirst("Host") != host) {
            respond(exchange, 404, "text/plain", ByteArray(0))
            return
        }
        val requestPath = exchange.requestURI.rawPath.orEmpty()
        val authenticated = exchange.requestHeaders.getFirst("Cookie")
            ?.split(';')?.any { it.trim() == "$cookieName=1" } == true
        val path = when {
            requestPath.startsWith("/$id/") -> requestPath
            authenticated -> "/$id$requestPath"
            else -> {
                respond(exchange, 404, "text/plain", ByteArray(0))
                return
            }
        }
        val query = exchange.requestURI.rawQuery?.let { "?$it" }.orEmpty()
        val original = mapper.toOriginalUrl("http://$host$path$query")
        val handler = interceptor
        if (original == null || handler == null) {
            respond(exchange, 404, "text/plain", ByteArray(0))
            return
        }
        val resource = try {
            runBlocking { handler.run(ResourceRequest(Url(original), HttpMethod.Get, Headers.Empty)) }
        } catch (_: Exception) {
            respond(exchange, 500, "text/plain", ByteArray(0))
            return
        }
        if (resource == null) {
            respond(exchange, 404, "text/plain", ByteArray(0))
            return
        }
        if (!authenticated) exchange.responseHeaders.add("Set-Cookie", "$cookieName=1; Path=/; HttpOnly; SameSite=Strict")
        val contentType = resource.contentType ?: "application/octet-stream"
        respond(exchange, 200, contentType, mapper.rewriteResource(contentType, resource.data))
    }

    private fun respond(exchange: HttpExchange, status: Int, contentType: String, data: ByteArray) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, data.size.toLong())
        exchange.responseBody.use { it.write(data) }
    }

    fun registerRequestInterceptor(handler: RequestInterceptor) { interceptor = handler }

    fun bind(name: String, callback: WebviewCallback) {
        val nativeName = "komelia_$name"
        onSwing {
            component.addJavascriptFunction(nativeName, AsyncJavascriptFunction { args ->
                val callId = nextCall.incrementAndGet().toString()
                val result = CompletableFuture<String>()
                pending[callId] = Pending(name, args, result)
                try {
                    callback.run(callId, args)
                } catch (error: Throwable) {
                    pending.remove(callId)?.result?.completeExceptionally(error)
                }
                result
            })
            val publicName = Json.encodeToString(name)
            val internalName = Json.encodeToString(nativeName)
            component.addOnBeforeLoad("window[$publicName]=(...args)=>window[$internalName](JSON.stringify(args)).then(JSON.parse);")
        }
    }

    fun navigate(uri: String) { onSwing { component.setUrl(mapper.toWebUrl(uri, serverBase = true)) } }

    fun resolve(id: String, encoded: String) {
        val call = pending.remove(id) ?: return
        val decoded = Base64.getDecoder().decode(encoded).decodeToString()
        call.result.complete(mapper.rewriteResult(call.name, decoded, call.request))
    }

    fun reject(id: String, response: String) {
        val call = pending.remove(id) ?: return
        val message = runCatching { Json.parseToJsonElement(response).jsonObject["result"]?.jsonPrimitive?.content }.getOrNull()
        call.result.completeExceptionally(IllegalStateException(message ?: "WebView request failed"))
    }

    fun close() {
        server.stop(0)
        serverExecutor.shutdownNow()
        pending.values.forEach { it.result.cancel(false) }
        pending.clear()
        onSwing { component.dispose() }
    }

    private fun onSwing(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) action() else SwingUtilities.invokeAndWait(action)
    }
}
