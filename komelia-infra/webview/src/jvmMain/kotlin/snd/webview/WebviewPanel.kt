package snd.webview

import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import snd.jni.DesktopPlatform

class WebviewPanel(
    onCreated: (KomeliaWebview) -> Unit
) : JPanel() {

    private var drawSurface: Component = Canvas().apply { background = Color.black }
    private var webview: KomeliaWebview? = null

    init {
        background = Color.black
        layout = BorderLayout()
        add(drawSurface, BorderLayout.CENTER)
        addAncestorListener(
            object : AncestorListener {
                override fun ancestorAdded(event: AncestorEvent) {
                    if (DesktopPlatform.Current == DesktopPlatform.MacOS) {
                        if (webview?.isClosed == false) return
                        remove(drawSurface)
                        val backend = MacWebviewBackend()
                        drawSurface = backend.component
                        add(drawSurface, BorderLayout.CENTER)
                        revalidate()
                        val webview = KomeliaWebview.macWebview(backend)
                        this@WebviewPanel.webview = webview
                        onCreated(webview)
                        return
                    }
                    KomeliaWebview.webview(drawSurface) { webview ->
                        this@WebviewPanel.webview = webview
                        webview.updateSize(event.component.width, event.component.height)
                        onCreated(webview)
                    }
                }

                override fun ancestorRemoved(event: AncestorEvent) {
                    if (DesktopPlatform.Current == DesktopPlatform.MacOS) webview?.close()
                }

                override fun ancestorMoved(event: AncestorEvent) {}
            }
        )
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                webview?.updateSize(e.component.width, e.component.height)
            }
        })
    }
}
