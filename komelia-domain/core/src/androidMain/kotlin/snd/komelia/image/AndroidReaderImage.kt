package snd.komelia.image

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Paint.FILTER_BITMAP_FLAG
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.StateFlow
import snd.komelia.image.AndroidBitmap.toBitmap
import snd.komelia.image.ReaderImage.PageId
import snd.komelia.image.processing.ImageProcessingPipeline

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual typealias RenderImage = Bitmap

class AndroidReaderImage(
    imageDecoder: KomeliaImageDecoder,
    imageSource: ImageSource,
    processingPipeline: ImageProcessingPipeline,
    stretchImages: StateFlow<Boolean>,
    pageId: PageId,
    upsamplingMode: StateFlow<UpsamplingMode>,
    downSamplingKernel: StateFlow<ReduceKernel>,
    linearLightDownSampling: StateFlow<Boolean>,
) : TilingReaderImage(
    imageDecoder = imageDecoder,
    imageSource = imageSource,
    processingPipeline = processingPipeline,
    stretchImages = stretchImages,
    upsamplingMode = upsamplingMode,
    downSamplingKernel = downSamplingKernel,
    linearLightDownSampling = linearLightDownSampling,
    pageId = pageId,
) {

    override fun closeTileBitmaps(tiles: List<ReaderImageTile>) {
        tiles.forEach { it.renderImage?.recycle() }
    }

    override suspend fun onUpsamplingModeChanged(mode: UpsamplingMode) {
        super.onUpsamplingModeChanged(mode)
        // High-quality modes change the raster pixels, including already visible/animated frames.
        reloadLastRequest()
    }

    override fun sourceTileSize(tileSize: Int, scaleFactor: Double): Int {
        if (upsamplingKernel() == null || scaleFactor <= 1.0) return tileSize
        // Keep enlarged tile bitmaps within the existing output-pixel budget while zooming.
        return (tileSize / scaleFactor).toInt().coerceIn(1, tileSize)
    }

    private fun upsamplingKernel(): ReduceKernel? = when (upsamplingMode.value) {
        UpsamplingMode.MITCHELL -> ReduceKernel.MITCHELL
        UpsamplingMode.LANCZOS3 -> ReduceKernel.LANCZOS3
        else -> null
    }

    override fun createTilePainter(
        tiles: List<ReaderImageTile>,
        displaySize: IntSize,
        scaleFactor: Double
    ): TiledPainter {
        return AndroidTiledPainter(
            tiles = tiles,
            upsamplingMode = upsamplingMode.value,
            scaleFactor = scaleFactor,
            displaySize = displaySize
        )
    }

    override suspend fun resizeImage(image: KomeliaImage, scaleWidth: Int, scaleHeight: Int): ReaderImageData {
        val kernel = upsamplingKernel()
        if (kernel != null && (scaleWidth > image.width || scaleHeight > image.pageHeight)) {
            val frames = mutableListOf<Bitmap>()
            try {
                for (index in 0 until image.pagesLoaded) {
                    image.extractArea(ImageRect(0, index * image.pageHeight, image.width, (index + 1) * image.pageHeight)).use { frame ->
                        frames.add(frame.upscaleBitmap(scaleWidth, scaleHeight, kernel))
                    }
                }
                return ReaderImageData(scaleWidth, scaleHeight, frames, image.pageDelays?.map { it.toLong() })
            } catch (error: Throwable) {
                frames.forEach { it.recycle() }
                throw error
            }
        }
        return image.resize(
            scaleWidth = scaleWidth,
            scaleHeight = scaleHeight,
            linear = linearLightDownSampling.value,
            kernel = downSamplingKernel.value
        ).use { it.toReaderImageData() }
    }

    override suspend fun getImageRegion(
        image: KomeliaImage,
        imageRegion: IntRect,
        scaleWidth: Int,
        scaleHeight: Int
    ): ReaderImageData {
        val kernel = upsamplingKernel()
        if (kernel != null && (scaleWidth > imageRegion.width || scaleHeight > imageRegion.height)) {
            // Include neighboring source pixels so reconstruction does not clamp at tile seams.
            val padding = if (kernel == ReduceKernel.LANCZOS3) 3 else 2
            val padded = ImageRect(
                (imageRegion.left - padding).coerceAtLeast(0),
                (imageRegion.top - padding).coerceAtLeast(0),
                (imageRegion.right + padding).coerceAtMost(image.width),
                (imageRegion.bottom + padding).coerceAtMost(image.height),
            )
            return image.extractArea(padded).use { region ->
                val sourceRegion = ImageRect(
                    imageRegion.left - padded.left,
                    imageRegion.top - padded.top,
                    imageRegion.right - padded.left,
                    imageRegion.bottom - padded.top,
                )
                ReaderImageData(scaleWidth, scaleHeight, listOf(region.upscaleBitmap(scaleWidth, scaleHeight, kernel, sourceRegion)), null)
            }
        }
        var region: KomeliaImage? = null
        var resized: KomeliaImage? = null
        try {
            region = image.extractArea(imageRegion.toImageRect())
            if (scaleWidth > imageRegion.width || scaleHeight > imageRegion.height) {
                val regionData = region.toReaderImageData()
                return regionData
            }
            resized = region.resize(
                scaleWidth = scaleWidth,
                scaleHeight = scaleHeight,
                linear = linearLightDownSampling.value,
                kernel = downSamplingKernel.value
            )
            return resized.toReaderImageData()
        } finally {
            region?.close()
            resized?.close()
        }
    }

    private suspend fun KomeliaImage.upscaleBitmap(
        targetWidth: Int,
        targetHeight: Int,
        kernel: ReduceKernel,
        sourceRegion: ImageRect = ImageRect(0, 0, width, height),
    ): Bitmap {
        check(type != ImageFormat.HISTOGRAM)
        val pixels = upscalePixels(getBytes(), width, height, bands, targetWidth, targetHeight, kernel, sourceRegion)
        return Bitmap.createBitmap(pixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    }

    private suspend fun KomeliaImage.toReaderImageData(): ReaderImageData {
        if (this.pagesLoaded == 1) {
            return ReaderImageData(width, height, listOf(this.toBitmap()), null)
        }

        val frames = mutableListOf<RenderImage>()
        val delays = pageDelays?.let { mutableListOf<Long>() }
        for (i in 0 until this.pagesLoaded) {
            val bitmap = this.extractArea(
                ImageRect(
                    left = 0,
                    right = width,
                    top = pageHeight * i,
                    bottom = pageHeight * (i + 1),
                )
            ).toBitmap()

            frames.add(bitmap)
            delays?.add(this.pageDelays?.getOrNull(i)?.toLong() ?: defaultFrameDelay)
        }
        return ReaderImageData(width, pageHeight, frames, delays)
    }

    private fun IntRect.toImageRect() =
        ImageRect(left = left, top = top, right = right, bottom = bottom)


    private class AndroidTiledPainter(
        private val tiles: List<ReaderImageTile>,
        private val upsamplingMode: UpsamplingMode,
        private val scaleFactor: Double,
        private val displaySize: IntSize,
    ) : TiledPainter() {
        override val intrinsicSize: Size = displaySize.toSize()
        private val paintFlags = when {
            scaleFactor > 1.0 && upsamplingMode != UpsamplingMode.NEAREST -> FILTER_BITMAP_FLAG
            else -> 0
        }

        override fun DrawScope.onDraw() {
            tiles.forEach { tile ->
                if (tile.renderImage != null && !tile.renderImage.isRecycled && tile.isVisible) {
                    val bitmap: Bitmap = tile.renderImage
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        bitmap,
                        null,
                        tile.displayRegion.toAndroidRectF(),
                        Paint().apply { flags = paintFlags },
                    )

//                    drawContext.canvas.drawRect(
//                        tile.displayRegion,
//                        Paint().apply {
//                            style = PaintingStyle.Stroke
//                            color = Color.Green
//                        }
//                    )
                }

            }
        }

        override fun withSamplingMode(upsamplingMode: UpsamplingMode): TiledPainter {
            return AndroidTiledPainter(
                tiles = tiles,
                upsamplingMode = upsamplingMode,
                scaleFactor = scaleFactor,
                displaySize = displaySize,
            )
        }
    }
}
