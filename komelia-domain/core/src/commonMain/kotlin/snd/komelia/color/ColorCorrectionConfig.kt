package snd.komelia.color

import kotlinx.serialization.Serializable

/** Snapshot values, not a reference to a mutable/deletable preset. */
@Serializable
data class ColorCorrectionConfig(
    val type: ColorCorrectionType,
    val curves: ColorCurvePoints = ColorCurvePoints.DEFAULT,
    val levels: ColorLevelChannels = ColorLevelChannels.DEFAULT,
)

@Serializable
data class DefaultColorCorrection(
    val presetName: String,
    val configuration: ColorCorrectionConfig,
)

enum class BookColorCorrectionMode {
    INHERIT,
    CUSTOM,
    DISABLED,
}
