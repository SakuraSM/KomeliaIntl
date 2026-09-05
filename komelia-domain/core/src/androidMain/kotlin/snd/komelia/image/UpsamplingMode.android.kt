package snd.komelia.image

actual fun availableUpsamplingModes() = listOf(
    UpsamplingMode.LANCZOS3,
    UpsamplingMode.MITCHELL,
    UpsamplingMode.BILINEAR,
    UpsamplingMode.NEAREST,
)
