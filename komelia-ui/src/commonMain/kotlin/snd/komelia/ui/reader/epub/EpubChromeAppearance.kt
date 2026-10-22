package snd.komelia.ui.reader.epub

import kotlinx.serialization.Serializable
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme

/** Optional host chrome settings. Publication styles and saved reading settings are separate. */
@Serializable
data class EpubChromeAppearance(
    val surfaceStyle: AppSurfaceStyle = AppSurfaceStyle.CLASSIC,
    val theme: AppTheme = AppTheme.DARK,
    val reduceTransparency: Boolean = false,
    val reducedMotion: Boolean = false,
    val renderingSupported: Boolean = true,
)
