package snd.komelia.db.settings

import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import snd.komelia.db.AppSettings
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.KomfSettings
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.settings.model.AppLanguage
import snd.komelia.settings.model.AppTheme
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MangaBakaSettingsIntegrationTest {
    @Test
    fun version17UpgradePreservesExistingSettingsAndMigrationChecksums() = runBlocking {
        val directory = createTempDirectory("komelia-mangabaka-migration").toString()
        val url = "jdbc:sqlite:$directory/komelia.sqlite"
        val oldMigration = Flyway.configure().dataSource(url, null, null)
            .resourceProvider(AppMigrations()).javaMigrationClassProvider(AppMigrations())
            .target("17").load()
        oldMigration.migrate()
        val checksums = migrationChecksums(url)
        val preferences = AppSettings(appLanguage = AppLanguage.ZH_CN, appTheme = AppTheme.DARKER)
        val reader = ImageReaderSettings(cropBorders = true, panelPrerenderCount = 2)
        val oldDatabase = Database.connect(url, driver = "org.sqlite.JDBC")
        ExposedSettingsRepository(oldDatabase).save(preferences)
        ExposedImageReaderSettingsRepository(oldDatabase).save(reader)
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO KomfSettings (version, enabled, remote_url) VALUES (1, 1, 'https://metadata.invalid')")
            }
        }

        val database = KomeliaDatabase(directory).app
        val repository = ExposedKomfSettingsRepository(database)
        assertEquals(KomfSettings(true, "https://metadata.invalid", false), repository.get())
        assertEquals(preferences, ExposedSettingsRepository(database).get())
        assertEquals(reader, ExposedImageReaderSettingsRepository(database).get())
        assertEquals(checksums, migrationChecksums(url).filterKeys { it.toInt() <= 17 })

        repository.save(KomfSettings(true, "https://metadata.invalid", true))
        val reopened = KomeliaDatabase(directory).app
        assertEquals(preferences, ExposedSettingsRepository(reopened).get())
        assertEquals(reader, ExposedImageReaderSettingsRepository(reopened).get())
        assertEquals(KomfSettings(true, "https://metadata.invalid", true), ExposedKomfSettingsRepository(reopened).get())
        assertEquals(checksums, migrationChecksums(url).filterKeys { it.toInt() <= 17 })
    }

    @Test
    fun freshAndLegacyBrowserSettingsDefaultToDisabled() = runBlocking {
        assertFalse(Json.decodeFromString<KomfSettings>("""{"enabled":true}""").mangaBakaEnabled)
        val directory = createTempDirectory("komelia-mangabaka-fresh").toString()
        val repository = ExposedKomfSettingsRepository(KomeliaDatabase(directory).app)
        repository.save(KomfSettings())
        assertEquals(KomfSettings(), repository.get())
    }

    private fun migrationChecksums(url: String): Map<String, Int?> =
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT version, checksum FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL").use { rows ->
                    buildMap {
                        while (rows.next()) {
                            val version = rows.getString("version")
                            val checksum = rows.getInt("checksum")
                            put(version, if (rows.wasNull()) null else checksum)
                        }
                    }
                }
            }
        }
}
