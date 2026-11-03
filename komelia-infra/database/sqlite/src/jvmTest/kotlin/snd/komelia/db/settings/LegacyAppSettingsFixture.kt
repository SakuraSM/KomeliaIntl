package snd.komelia.db.settings

import java.sql.DriverManager
import snd.komelia.db.AppSettings
import snd.komelia.db.ImageReaderSettings
import io.github.vinceglb.filekit.path
import kotlinx.serialization.json.Json

/** Seed the released schema without using a repository that knows about later columns. */
internal fun saveVersion17AppSettings(url: String, settings: AppSettings) {
    val values = linkedMapOf<String, Any?>(
        "version" to 1,
        "username" to settings.username,
        "serverUrl" to settings.serverUrl,
        "lan_server_url" to settings.lanServerUrl,
        "lan_auto_switch_enabled" to settings.lanAutoSwitchEnabled,
        "card_width" to settings.cardWidth,
        "series_page_load_size" to settings.seriesPageLoadSize,
        "book_page_load_size" to settings.bookPageLoadSize,
        "book_list_layout" to settings.bookListLayout.name,
        "app_theme" to settings.appTheme.name,
        "app_language" to settings.appLanguage.name,
        "check_for_updates_on_startup" to settings.checkForUpdatesOnStartup,
        "update_last_checked_timestamp" to settings.updateLastCheckedTimestamp?.toString(),
        "update_last_checked_release_version" to settings.updateLastCheckedReleaseVersion?.toString(),
        "update_dismissed_version" to settings.updateDismissedVersion?.toString(),
    )
    DriverManager.getConnection(url).use { connection ->
        val columns = values.keys.joinToString(",")
        val placeholders = values.keys.joinToString(",") { "?" }
        connection.prepareStatement("INSERT INTO AppSettings ($columns) VALUES ($placeholders)").use { statement ->
            values.values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }
}

/** Keep this column list on the released reader schema, independently of the current repository. */
internal fun saveVersion17ImageReaderSettings(url: String, settings: ImageReaderSettings) {
    val values = linkedMapOf<String, Any?>(
        "book_id" to "DEFAULT",
        "reader_type" to settings.readerType.name,
        "stretch_to_fit" to settings.stretchToFit,
        "paged_scale_type" to settings.pagedScaleType.name,
        "paged_reading_direction" to settings.pagedReadingDirection.name,
        "paged_page_layout" to settings.pagedPageLayout.name,
        "panel_prerender_count" to settings.panelPrerenderCount,
        "continuous_reading_direction" to settings.continuousReadingDirection.name,
        "continuous_padding" to settings.continuousPadding,
        "continuous_page_spacing" to settings.continuousPageSpacing,
        "crop_borders" to settings.cropBorders,
        "load_thumbnail_previews" to settings.loadThumbnailPreviews,
        "volume_keys_navigation" to settings.volumeKeysNavigation,
        "flash_on_page_change" to settings.flashOnPageChange,
        "flash_duration" to settings.flashDuration,
        "flash_every_n_pages" to settings.flashEveryNPages,
        "flash_with" to settings.flashWith.name,
        "downsampling_kernel" to settings.downsamplingKernel.name,
        "linear_light_downsampling" to settings.linearLightDownsampling,
        "upsampling_mode" to settings.upsamplingMode.name,
        "onnx_runtime_device_id" to settings.ortUpscalerDeviceId,
        "onnx_runtime_mode" to settings.ortUpscalerMode.name,
        "onnx_runtime_tile_size" to settings.ortUpscalerTileSize,
        "onnx_runtime_model_path" to settings.ortUpscalerUserModelPath?.path,
        "default_color_correction" to settings.defaultColorCorrection?.let { Json.encodeToString(it) },
    )
    DriverManager.getConnection(url).use { connection ->
        val columns = values.keys.joinToString(",")
        val placeholders = values.keys.joinToString(",") { "?" }
        connection.prepareStatement("INSERT INTO ImageReaderSettings ($columns) VALUES ($placeholders)").use { statement ->
            values.values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }
}
