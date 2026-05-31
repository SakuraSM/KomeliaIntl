package snd.komelia.api

import snd.komelia.komga.api.KomgaBookApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komelia.offline.book.repository.OfflineBookRepository
import snd.komga.client.book.KomgaBookId
import snd.komga.client.book.KomgaBookMetadataUpdateRequest
import snd.komga.client.book.KomgaBookPage
import snd.komga.client.book.KomgaBookReadProgressUpdateRequest
import snd.komga.client.book.KomgaBookSearch
import snd.komga.client.book.KomgaBookThumbnail
import snd.komga.client.book.R2Positions
import snd.komga.client.book.R2Progression
import snd.komga.client.book.WPPublication
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaThumbnailId
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.search.BookConditionBuilder

class LocalFirstBookApi(
    private val remoteBookApi: KomgaBookApi,
    private val offlineBookApi: KomgaBookApi,
    private val offlineBookRepository: OfflineBookRepository,
) : KomgaBookApi {

    override suspend fun getOne(bookId: KomgaBookId): KomeliaBook {
        return localFirst(bookId, { offlineBookApi.getOne(bookId) }, { remoteBookApi.getOne(bookId) })
    }

    override suspend fun getBookList(
        conditionBuilder: BookConditionBuilder,
        fullTextSearch: String?,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        return remoteBookApi.getBookList(conditionBuilder, fullTextSearch, pageRequest)
    }

    override suspend fun getBookList(
        search: KomgaBookSearch,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        return remoteBookApi.getBookList(search, pageRequest)
    }

    override suspend fun getLatestBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> {
        return remoteBookApi.getLatestBooks(pageRequest)
    }

    override suspend fun getBooksOnDeck(
        libraryIds: List<KomgaLibraryId>?,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        return remoteBookApi.getBooksOnDeck(libraryIds, pageRequest)
    }

    override suspend fun getDuplicateBooks(pageRequest: KomgaPageRequest?): Page<KomeliaBook> {
        return remoteBookApi.getDuplicateBooks(pageRequest)
    }

    override suspend fun getBookSiblingPrevious(bookId: KomgaBookId): KomeliaBook? {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getBookSiblingPrevious(bookId) },
            remote = { remoteBookApi.getBookSiblingPrevious(bookId) }
        )
    }

    override suspend fun getBookSiblingNext(bookId: KomgaBookId): KomeliaBook? {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getBookSiblingNext(bookId) },
            remote = { remoteBookApi.getBookSiblingNext(bookId) }
        )
    }

    override suspend fun updateMetadata(bookId: KomgaBookId, request: KomgaBookMetadataUpdateRequest) {
        remoteBookApi.updateMetadata(bookId, request)
    }

    override suspend fun getBookPages(bookId: KomgaBookId): List<KomgaBookPage> {
        return localFirst(bookId, { offlineBookApi.getBookPages(bookId) }, { remoteBookApi.getBookPages(bookId) })
    }

    override suspend fun analyze(bookId: KomgaBookId) {
        remoteBookApi.analyze(bookId)
    }

    override suspend fun refreshMetadata(bookId: KomgaBookId) {
        remoteBookApi.refreshMetadata(bookId)
    }

    override suspend fun markReadProgress(bookId: KomgaBookId, request: KomgaBookReadProgressUpdateRequest) {
        localFirst(bookId, { offlineBookApi.markReadProgress(bookId, request) }, {
            remoteBookApi.markReadProgress(bookId, request)
        })
    }

    override suspend fun deleteReadProgress(bookId: KomgaBookId) {
        localFirst(bookId, { offlineBookApi.deleteReadProgress(bookId) }, { remoteBookApi.deleteReadProgress(bookId) })
    }

    override suspend fun deleteBook(bookId: KomgaBookId) {
        remoteBookApi.deleteBook(bookId)
    }

    override suspend fun regenerateThumbnails(forBiggerResultOnly: Boolean) {
        remoteBookApi.regenerateThumbnails(forBiggerResultOnly)
    }

    override suspend fun getDefaultThumbnail(bookId: KomgaBookId): ByteArray? {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getDefaultThumbnail(bookId) },
            remote = { remoteBookApi.getDefaultThumbnail(bookId) }
        )
    }

    override suspend fun getThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId): ByteArray {
        return remoteBookApi.getThumbnail(bookId, thumbnailId)
    }

    override suspend fun getThumbnails(bookId: KomgaBookId): List<KomgaBookThumbnail> {
        return remoteBookApi.getThumbnails(bookId)
    }

    override suspend fun uploadThumbnail(
        bookId: KomgaBookId,
        file: ByteArray,
        filename: String,
        selected: Boolean
    ): KomgaBookThumbnail {
        return remoteBookApi.uploadThumbnail(bookId, file, filename, selected)
    }

    override suspend fun selectBookThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId) {
        remoteBookApi.selectBookThumbnail(bookId, thumbnailId)
    }

    override suspend fun deleteBookThumbnail(bookId: KomgaBookId, thumbnailId: KomgaThumbnailId) {
        remoteBookApi.deleteBookThumbnail(bookId, thumbnailId)
    }

    override suspend fun getAllReadListsByBook(bookId: KomgaBookId): List<KomgaReadList> {
        return remoteBookApi.getAllReadListsByBook(bookId)
    }

    override suspend fun getPage(bookId: KomgaBookId, page: Int): ByteArray {
        return localFirst(bookId, { offlineBookApi.getPage(bookId, page) }, { remoteBookApi.getPage(bookId, page) })
    }

    override suspend fun getPageThumbnail(bookId: KomgaBookId, page: Int): ByteArray {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getPageThumbnail(bookId, page) },
            remote = { remoteBookApi.getPageThumbnail(bookId, page) }
        )
    }

    override suspend fun getReadiumProgression(bookId: KomgaBookId): R2Progression? {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getReadiumProgression(bookId) },
            remote = { remoteBookApi.getReadiumProgression(bookId) }
        )
    }

    override suspend fun updateReadiumProgression(bookId: KomgaBookId, progression: R2Progression) {
        localFirst(
            bookId = bookId,
            local = { offlineBookApi.updateReadiumProgression(bookId, progression) },
            remote = { remoteBookApi.updateReadiumProgression(bookId, progression) }
        )
    }

    override suspend fun getReadiumPositions(bookId: KomgaBookId): R2Positions {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getReadiumPositions(bookId) },
            remote = { remoteBookApi.getReadiumPositions(bookId) }
        )
    }

    override suspend fun getWebPubManifest(bookId: KomgaBookId): WPPublication {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getWebPubManifest(bookId) },
            remote = { remoteBookApi.getWebPubManifest(bookId) }
        )
    }

    override suspend fun getBookEpubResource(bookId: KomgaBookId, resourceName: String): ByteArray {
        return localFirst(
            bookId = bookId,
            local = { offlineBookApi.getBookEpubResource(bookId, resourceName) },
            remote = { remoteBookApi.getBookEpubResource(bookId, resourceName) }
        )
    }

    private suspend fun <T> localFirst(
        bookId: KomgaBookId,
        local: suspend () -> T,
        remote: suspend () -> T,
    ): T {
        return if (offlineBookRepository.exists(bookId)) local()
        else remote()
    }

}
