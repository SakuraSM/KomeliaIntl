package snd.komelia.ui.settings.local

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.library.model.OfflineLibrary
import snd.komelia.offline.local.LocalLibraryManager
import snd.komelia.offline.local.LocalLibraryScanState
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.library.ScanInterval

class LocalLibraryViewModel(
    private val manager: LocalLibraryManager?,
) : ScreenModel {
    var libraries by mutableStateOf<List<OfflineLibrary>>(emptyList())
        private set
    var books by mutableStateOf<List<KomeliaBook>>(emptyList())
        private set
    var currentPage by mutableStateOf(1)
        private set
    var totalPages by mutableStateOf(0)
        private set
    var totalBooks by mutableStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val isAvailable: Boolean = manager != null
    val scanState: StateFlow<LocalLibraryScanState> = manager?.scanState
        ?: MutableStateFlow(LocalLibraryScanState(error = "Local folders are not available on this platform"))

    fun initialize() = reload()

    fun addLibrary(directory: PlatformFile) = launchAction {
        manager?.addLibrary(
            root = directory,
            name = directory.name.ifBlank { "Local library" },
        )
    }

    fun refresh(libraryId: KomgaLibraryId) = launchAction { manager?.scan(libraryId) }

    fun remove(libraryId: KomgaLibraryId) = launchAction { manager?.removeLibrary(libraryId) }

    fun setScheduled(libraryId: KomgaLibraryId, enabled: Boolean) = launchAction {
        manager?.updateScanInterval(libraryId, if (enabled) ScanInterval.HOURLY else ScanInterval.DISABLED)
    }

    fun setPage(page: Int) {
        val target = page.coerceIn(1, totalPages.coerceAtLeast(1))
        if (target == currentPage) return
        currentPage = target
        reload()
    }

    fun reload() = launchAction { }

    private fun launchAction(action: suspend () -> Unit) {
        screenModelScope.launch {
            loading = true
            error = null
            try {
                action()
                libraries = manager?.getLibraries().orEmpty()
                var bookPage = manager?.getBooks(
                    KomgaPageRequest(pageIndex = currentPage - 1, size = LOCAL_LIBRARY_PAGE_SIZE),
                )
                if (bookPage != null && bookPage.totalPages > 0 && currentPage > bookPage.totalPages) {
                    currentPage = bookPage.totalPages
                    bookPage = manager?.getBooks(
                        KomgaPageRequest(pageIndex = currentPage - 1, size = LOCAL_LIBRARY_PAGE_SIZE),
                    )
                }
                books = bookPage?.content.orEmpty()
                totalPages = bookPage?.totalPages ?: 0
                totalBooks = bookPage?.totalElements ?: 0
                if (totalPages == 0) currentPage = 1
            } catch (throwable: Throwable) {
                error = throwable.message ?: throwable::class.simpleName
            } finally {
                loading = false
            }
        }
    }

    private companion object {
        const val LOCAL_LIBRARY_PAGE_SIZE = 24
    }
}
