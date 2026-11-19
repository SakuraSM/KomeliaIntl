package snd.webview

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.URI
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

class MacWebviewIntegrationTest {
    @Test fun panelClosesItsReaderAndRecreatesItOnReattachment() {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        val readers = mutableListOf<KomeliaWebview>()
        val firstCreated = CountDownLatch(1)
        val secondCreated = CountDownLatch(1)
        var frame: JFrame? = null
        lateinit var panel: WebviewPanel
        try {
            SwingUtilities.invokeAndWait {
                panel = WebviewPanel {
                    readers.add(it)
                    if (readers.size == 1) firstCreated.countDown() else secondCreated.countDown()
                }
                frame = JFrame("Komelia synthetic reader lifecycle test").apply {
                    setSize(400, 300)
                    add(panel)
                    isVisible = true
                }
            }
            assertTrue(firstCreated.await(15, TimeUnit.SECONDS), "Reader was not created")
            SwingUtilities.invokeAndWait {
                val window = requireNotNull(frame)
                window.remove(panel)
                window.validate()
                assertTrue(readers.first().isClosed, "Detached reader was not closed")
                window.add(panel)
                window.validate()
            }
            assertTrue(secondCreated.await(15, TimeUnit.SECONDS), "Reader was not recreated")
            assertTrue(readers.size == 2 && !readers.last().isClosed, "Recreated reader is closed")
        } finally {
            readers.forEach { it.close() }
            frame?.let { window -> SwingUtilities.invokeAndWait { window.dispose() } }
        }
    }

    @Test fun pageUsesNativeBindingsAndLoadsPrivateBookResources() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        lateinit var backend: MacWebviewBackend
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait {
            backend = MacWebviewBackend()
            frame = JFrame("Komelia synthetic macOS WebView test").apply {
                setSize(700, 500)
                add(backend.component)
                isVisible = true
            }
        }
        val webview = KomeliaWebview.macWebview(backend)
        val completed = CountDownLatch(1)
        var result = ""
        try {
            webview.registerRequestInterceptor { request ->
                when (request.url.encodedPath) {
                    "/index.html" -> ResourceLoadResult(
                        """<meta charset="UTF-8"><h1>Synthetic reader</h1><script>
                            Promise.all([window.hello(),window.echo('中文'),window.fail().catch(e=>e.message),
                              fetch('/api/v1/books/1/resource/test.txt').then(r=>r.text()),
                              fetch('/api/v1/books/1/resource/large.bin').then(r=>r.arrayBuffer()).then(b=>b.byteLength),
                              Promise.resolve(window.location.href)])
                              .then(values=>window.report(JSON.stringify(values)));
                        </script>""".encodeToByteArray(), "text/html"
                    )
                    "/api/v1/books/1/resource/test.txt" -> ResourceLoadResult("book resource".encodeToByteArray(), "text/plain")
                    "/api/v1/books/1/resource/large.bin" -> ResourceLoadResult(ByteArray(65 * 1024 * 1024), "application/octet-stream")
                    else -> null
                }
            }
            webview.bind<Unit, String>("hello") { "ready" }
            webview.bind<String, String>("echo") { it }
            webview.bind<Unit, String>("fail") { error("synthetic failure") }
            webview.bind<String, Unit>("report") { result = it; completed.countDown() }
            webview.navigate("http://komelia/index.html")
            webview.start()
            assertTrue(completed.await(30, TimeUnit.SECONDS), "Page did not call the native bridge")
            assertTrue(result.contains("ready") && result.contains("中文") && result.contains("book resource") &&
                result.contains("synthetic failure") && result.contains("68157440"), result)
            val location = URI(Json.parseToJsonElement(result).jsonArray.last().jsonPrimitive.content)
            val untrusted = URI("http://127.0.0.1:${location.port}/api/v1/books/1/resource/test.txt")
                .toURL().openConnection() as java.net.HttpURLConnection
            assertTrue(untrusted.responseCode == 404, "A request without the session capability was accepted")
            untrusted.disconnect()
        } finally {
            webview.close()
            SwingUtilities.invokeAndWait { frame.dispose() }
        }
    }
}
