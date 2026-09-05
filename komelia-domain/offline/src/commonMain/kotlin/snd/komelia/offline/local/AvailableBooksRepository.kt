package snd.komelia.offline.local

import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.OfflineRepositories
import snd.komelia.offline.user.model.OfflineUser
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.Page
import snd.komga.client.search.anyOfBooks

enum class AvailableBookSource {
    ALL,
    LOCAL,
    DOWNLOADED,
}

class AvailableBooksRepository(
    private val repositories: OfflineRepositories,
) {
    suspend fun getBooks(
        source: AvailableBookSource = AvailableBookSource.ALL,
        query: String = "",
        pageRequest: KomgaPageRequest = KomgaPageRequest(unpaged = true),
    ): Page<KomeliaBook> {
        val libraryIds = when (source) {
            AvailableBookSource.ALL -> null
            AvailableBookSource.LOCAL -> repositories.libraryRepository.findAll()
                .filter { it.isLocalSourceLibrary() }
                .map { it.id }
                .toSet()

            AvailableBookSource.DOWNLOADED -> repositories.libraryRepository.findAll()
                .filterNot { it.isLocalSourceLibrary() }
                .map { it.id }
                .toSet()
        }
        if (libraryIds != null && libraryIds.isEmpty()) return Page.empty()
        val condition = libraryIds?.let {
            anyOfBooks {
                it.forEach { libraryId -> library { isEqualTo(libraryId) } }
            }.toBookCondition()
        }
        return repositories.bookDtoRepository.findAll(
            userId = OfflineUser.ROOT,
            search = KomgaBookSearch(
                condition = condition,
                fullTextSearch = query.trim().takeIf(String::isNotEmpty),
            ),
            pageRequest = pageRequest,
        )
    }
}
