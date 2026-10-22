package snd.komelia.db.settings

import java.sql.DriverManager
import snd.komelia.db.AppSettings

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
