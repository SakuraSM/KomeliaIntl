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
import org.w3c.dom.asList
import org.w3c.dom.get
import snd.komelia.KomfActiveDialog
import snd.komelia.ui.Theme
import snd.komf.api.KomfServerLibraryId
import snd.komf.api.KomfServerSeriesId

class KomgaSeriesActions(
    private val theme: StateFlow<Theme>,
    private val currentDialog: MutableStateFlow<KomfActiveDialog>,
) {
    private val element: HTMLButtonElement = document.createElement("button") as HTMLButtonElement
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val dropdown = KomgaDropdown(
        parent = element,
        items = listOf(
            KomgaDropdown.DropdownItem("Identify", this::onIdentifyClick),
            KomgaDropdown.DropdownItem("Reset Metadata", this::onResetMetadataClick),
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

    private fun onIdentifyClick() {
        val seriesId = getSeriesId()
        val libraryId = getLibraryId()
        val seriesTitle = getSeriesTitle()
        when {
            seriesId == null -> currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to fine seriesId")
            libraryId == null -> currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to find libraryId")
            seriesTitle == null -> currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to find series title")
            else -> currentDialog.value = KomfActiveDialog.SeriesIdentify(
                seriesId = seriesId,
                libraryId = libraryId,
                seriesTitle = seriesTitle
            )
        }
    }

    private fun onResetMetadataClick() {
        val seriesId = getSeriesId()
        val libraryId = getLibraryId()
        when {
            seriesId == null -> currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to fine seriesId")
            libraryId == null -> currentDialog.value = KomfActiveDialog.ErrorDialog("Failed to find libraryId")
            else -> currentDialog.value = KomfActiveDialog.SeriesReset(
                seriesId = seriesId,
                libraryId = libraryId,
            )
        }
    }

    fun tryMount(parent: HTMLElement): Boolean {
        if (parent.contains(element)) return true

        val toolbar = parent.querySelector(".v-main__wrap .v-toolbar__content")
        val toolbarParent = toolbar?.parentElement

        if (toolbar != null && toolbarParent != null && !toolbarParent.classList.contains("hidden-sm-and-up")) {
            val path = window.location.pathname.split("/").dropLast(1).reversed()
            if (path.any { it == "series" }) {
                toolbar.children[4]?.insertAdjacentElement("afterend", element)
                dropdown.tryMount()
                return true
            } else if (path.any { it == "oneshot" }) {
                toolbar.children.asList()
                    .find { it.tagName == "BUTTON" }
                    ?.insertAdjacentElement("afterend", element)
                dropdown.tryMount()

                return true
            }
        }

        return false
    }

    fun getSeriesTitle(): String? {
        val seriesTitle = document.querySelector(
            ".v-main__wrap .v-toolbar__content .v-toolbar__title span"
        ) as? HTMLElement
        if (seriesTitle != null) return seriesTitle.innerText

        val oneshotTitle = document.querySelector(
            ".v-main__wrap .container--fluid .container span.text-h6"
        ) as? HTMLElement
        if (oneshotTitle != null) return oneshotTitle.innerText

        return null
    }

    fun getSeriesId(): KomfServerSeriesId? {
        val urlPath = window.location.pathname.split("/")
        val idx = urlPath.indexOfFirst { it == "series" || it == "oneshot" }
        if (idx == -1) return null
        val seriesId = urlPath.getOrNull(idx + 1)?.let { KomfServerSeriesId(it) }
        return seriesId
    }


    fun getLibraryId(): KomfServerLibraryId? {
        val toolbar = document.querySelector(".v-main__wrap .v-toolbar__content") ?: return null
        val libraryHref = toolbar.children.asList().find {
            val href = it.getAttribute("href") ?: return@find false
            href.contains("libraries")
        }?.getAttribute("href") ?: return null

        return libraryHref.split("/")
            .getOrNull(libraryHref.indexOf("libraries") + 1)
            ?.let { KomfServerLibraryId(it) }
    }
}