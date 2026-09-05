package snd.komelia.image

enum class UpsamplingMode {
    NEAREST,
    BILINEAR,
    MITCHELL,
    CATMULL_ROM,
    LANCZOS3,
}

expect fun availableUpsamplingModes(): List<UpsamplingMode>
