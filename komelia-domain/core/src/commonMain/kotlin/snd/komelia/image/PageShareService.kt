package snd.komelia.image

data class SharedPageImage(val encodedImage: ByteArray, val pageNumber: Int)

fun interface PageShareService {
    suspend fun share(image: SharedPageImage)
}
