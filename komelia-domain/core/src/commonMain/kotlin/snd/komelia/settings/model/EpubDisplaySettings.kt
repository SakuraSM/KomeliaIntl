package snd.komelia.settings.model

import kotlinx.serialization.Serializable

/** Native reader viewport settings, separate from each web reader's settings. */
@Serializable
data class EpubDisplaySettings(
    val immersiveMode: Boolean = false,
    val extraTopSpacingDp: Int = 8,
) {
    val topSpacingDp: Int get() = extraTopSpacingDp.coerceIn(0, 48)
}
