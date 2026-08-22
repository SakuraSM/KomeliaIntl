package snd.komelia.komga

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import snd.komelia.KomfActiveDialog
import snd.komelia.ui.Theme
import snd.komf.api.KomfServerLibraryId

class KomgaLibraryActions(
    private val theme: StateFlow<Theme>,
    private val currentDialog: MutableStateFlow<KomfActiveDialog>,
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val element: HTMLButtonElement = document.createElement("button") as HTMLButtonElement
    private val dropdown = KomgaDropdown(
        parent = element,
        items = listOf(
            KomgaDropdown.DropdownItem("Auto-Identify", this::onIdentifyClick),
            KomgaDropdown.DropdownItem("Reset Metadata", this::onResetClick),
        ),
        theme = theme
    )

    init {
        element.type = "button"
        element.classList.value = "v-btn v-btn--icon v-btn--round theme--dark v-size--default"
        element.innerHTML =
            "<span class=\"v-btn__content\"><i aria-hidden=\"true\" class=\"v-icon notranslate mdi mdi-puzzle theme--dark\"></i></span>"
        element.addEventListener("focus") { event -> (event.target as HTMLElement).blur() }

        theme.onEach { element.changeTheme(it) }.launchIn(coroutineScope)
    }

    fun tryMount(parent: HTMLElement): Boolean {
        if (parent.contains(element)) return true

        val toolbar = parent.querySelector(".v-main__wrap .v-toolbar__content")
        val toolbarParent = toolbar?.parentElement
        if (toolbar != null && toolbarParent != null && !toolbarParent.classList.contains("hidden-sm-and-up")) {
            val path = window.location.pathname.split("/").reversed()
            if (path.any { it == "libraries" }) {
                toolbar.children[4]?.insertAdjacentElement("afterend", element)

                dropdown.tryMount()
                return true
            }
        }

        return false
    }

    private fun onIdentifyClick() {
        val libraryId = getLibraryId()
        if (libraryId == null) currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to fine libraryId")
        else currentDialog.value = KomfActiveDialog.LibraryIdentify(libraryId)
    }

    private fun onResetClick() {
        val libraryId = getLibraryId()
        if (libraryId == null) currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to fine libraryId")
        else currentDialog.value = KomfActiveDialog.LibraryReset(libraryId)
    }

    fun getLibraryId(): KomfServerLibraryId? {
        val urlPath = window.location.pathname.split("/")
        val idx = urlPath.indexOfFirst { it == "libraries" }
        if (idx == -1) return null
        val libraryId = urlPath.getOrNull(idx + 1)?.let { KomfServerLibraryId(it) }
        return libraryId
    }
}
