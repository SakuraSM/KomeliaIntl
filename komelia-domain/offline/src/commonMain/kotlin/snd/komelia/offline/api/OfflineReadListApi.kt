package snd.komelia.offline.api

import snd.komelia.komga.api.KomgaReadListApi
import snd.komelia.komga.api.model.KomeliaBook
import snd.komga.client.book.KomgaBookId
import snd.komga.client.common.KomgaPageRequest
import snd.komga.client.common.KomgaThumbnailId
import snd.komga.client.common.Page
import snd.komga.client.library.KomgaLibraryId
import snd.komga.client.readlist.KomgaReadList
import snd.komga.client.readlist.KomgaReadListCreateRequest
import snd.komga.client.readlist.KomgaReadListId
import snd.komga.client.readlist.KomgaReadListQuery
import snd.komga.client.readlist.KomgaReadListThumbnail
import snd.komga.client.readlist.KomgaReadListUpdateRequest
import kotlin.time.Clock

class OfflineReadListApi : KomgaReadListApi {
    override suspend fun getAll(
        search: String?,
        libraryIds: List<KomgaLibraryId>?,
        pageRequest: KomgaPageRequest?
    ): Page<KomgaReadList> {
        return Page.empty()
    }

    override suspend fun getOne(id: KomgaReadListId): KomgaReadList {
        val now = Clock.System.now()
        return KomgaReadList(
            id = id,
            name = "",
            summary = "",
            ordered = false,
            bookIds = emptyList(),
            createdDate = now,
            lastModifiedDate = now,
            filtered = false,
        )
    }

    override suspend fun addOne(request: KomgaReadListCreateRequest): KomgaReadList {
        val now = Clock.System.now()
        return KomgaReadList(
            id = KomgaReadListId(""),
            name = request.name,
            summary = request.summary,
            ordered = request.ordered,
            bookIds = emptyList(),
            createdDate = now,
            lastModifiedDate = now,
            filtered = false,
        )
    }

    override suspend fun updateOne(
        id: KomgaReadListId,
        request: KomgaReadListUpdateRequest
    ) {
    }

    override suspend fun deleteOne(id: KomgaReadListId) {
    }

    override suspend fun getBooksForReadList(
        id: KomgaReadListId,
        query: KomgaReadListQuery?,
        pageRequest: KomgaPageRequest?
    ): Page<KomeliaBook> {
        return Page.empty()
    }

    override suspend fun getDefaultThumbnail(readListId: KomgaReadListId): ByteArray? {
        return null
    }

    override suspend fun getThumbnail(
        readListId: KomgaReadListId,
        thumbnailId: KomgaThumbnailId
    ): ByteArray {
        return ByteArray(0)
    }

    override suspend fun getThumbnails(readListId: KomgaReadListId): List<KomgaReadListThumbnail> {
        return emptyList()
    }

    override suspend fun uploadThumbnail(
        readListId: KomgaReadListId,
        file: ByteArray,
        filename: String,
        selected: Boolean
    ): KomgaReadListThumbnail {
        val thumbnailId = KomgaThumbnailId("")
        return KomgaReadListThumbnail(
            id = thumbnailId,
            readListId = readListId,
            type = "",
            selected = selected,
            mediaType = "",
            fileSize = file.size.toLong(),
            width = 0,
            height = 0,
        )
    }

    override suspend fun selectThumbnail(
        readListId: KomgaReadListId,
        thumbnailId: KomgaThumbnailId
    ) {
    }

    override suspend fun deleteThumbnail(
        readListId: KomgaReadListId,
        thumbnailId: KomgaThumbnailId
    ) {
    }

    override suspend fun getBookSiblingNext(
        readListId: KomgaReadListId,
        bookId: KomgaBookId
    ): KomeliaBook? {
        return null
    }

    override suspend fun getBookSiblingPrevious(
        readListId: KomgaReadListId,
        bookId: KomgaBookId
    ): KomeliaBook? {
        return null
    }
}
