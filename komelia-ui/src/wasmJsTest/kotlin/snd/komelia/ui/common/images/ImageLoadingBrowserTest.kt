package snd.komelia.ui.common.images

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImageLoadingBrowserTest {
    @Test fun coilDecodesBitmapWithTheSelectedWebSkikoRuntime() = runTest {
        withContext(Dispatchers.Default) {
            val context = PlatformContext.INSTANCE
            val loader = ImageLoader.Builder(context).memoryCache(null).diskCache(null).build()
            try {
                // A generated 2×2 PNG exercises Coil's decoder and Skiko bindings without a server.
                val png = "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR4nGMwTjvz//+ZtP8MMAYAXuYLKcxGoDAAAAAASUVORK5CYII="
                val result = loader.execute(
                    ImageRequest.Builder(context).data("data:image/png;base64,$png").build(),
                )
                val success = assertIs<SuccessResult>(result, (result as? ErrorResult)?.throwable.toString())
                assertEquals(2, success.image.width)
                assertEquals(2, success.image.height)
            } finally {
                loader.shutdown()
            }
        }
    }
}
