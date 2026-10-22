package snd.komelia.ui.accessibility

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the browser's real semantics mirror, including the dependency's popup lifecycle. */
class PopupSemanticsBrowserTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun mainPageKeepsUpdatingAfterPopupAndDialogDismissal() = runTest {
        withContext(Dispatchers.Default) {
            val host = document.createElement("div") as HTMLDivElement
            host.style.width = "640px"
            host.style.height = "480px"
            document.body!!.appendChild(host)
            var active by mutableStateOf(true)
            var popup by mutableStateOf(false)
            var dialog by mutableStateOf(false)
            var revision by mutableStateOf(0)
            try {
                ComposeViewport(host) {
                    if (active) MaterialTheme {
                        Column {
                            Text("Main page revision $revision")
                            if (popup) Popup(onDismissRequest = { popup = false }) {
                                Text("Popup choice")
                            }
                            if (dialog) Dialog(onDismissRequest = { dialog = false }) {
                                Text("Modal contents")
                            }
                        }
                    }
                }
                // The application also resolves dependencies asynchronously after creating its viewport.
                delay(250)
                revision++
                awaitSemantics(host, "Main page revision $revision")
                repeat(2) {
                    popup = true
                    awaitSemantics(host, "Popup choice")
                    popup = false
                    revision++
                    awaitSemantics(host, "Main page revision $revision", removedText = "Popup choice")
                    assertFalse(semanticsText(host).contains("Popup choice"), "Dismissed popup must leave the accessibility tree")

                    dialog = true
                    awaitSemantics(host, "Modal contents")
                    dialog = false
                    revision++
                    awaitSemantics(host, "Main page revision $revision", removedText = "Modal contents")
                    assertFalse(semanticsText(host).contains("Modal contents"), "Dismissed dialog must leave the accessibility tree")
                }
            } finally {
                active = false
                delay(50)
                host.remove()
            }
        }
    }

    private suspend fun awaitSemantics(host: HTMLElement, text: String, removedText: String? = null) {
        fun matches(): Boolean {
            val content = semanticsText(host)
            return content.contains(text) && (removedText == null || !content.contains(removedText))
        }
        repeat(200) {
            if (matches()) return
            delay(25)
        }
        assertTrue(
            matches(),
            "Browser accessibility tree did not publish '$text': ${semanticsText(host)}; ${semanticsDebug(host)}",
        )
    }

    private fun semanticsText(host: HTMLElement): String {
        val elements = host.querySelectorAll("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            val root = element.shadowRoot?.querySelector("#cmp_a11y_root")
            if (root != null) return root.textContent.orEmpty()
        }
        return ""
    }

    private fun semanticsDebug(host: HTMLElement): String {
        val elements = host.querySelectorAll("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            val root = element.shadowRoot ?: continue
            return "canvas=${root.querySelector("canvas")?.outerHTML}; semantics=${root.querySelector("#cmp_a11y_root")?.outerHTML}"
        }
        return "host=${host.innerHTML.take(1200)}"
    }
}
