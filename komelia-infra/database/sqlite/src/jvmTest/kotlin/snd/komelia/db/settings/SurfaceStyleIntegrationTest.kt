package snd.komelia.db.settings

import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import snd.komelia.db.AppSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.settings.model.AppLanguage
import snd.komelia.settings.model.AppSurfaceStyle
import snd.komelia.settings.model.AppTheme
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SurfaceStyleIntegrationTest {
    @Test
    fun upgradesFromBothReleasedAndUpstreamSchemasPreserveExistingChoices() = runBlocking {
        for (version in listOf("17", "18")) {
            val directory = createTempDirectory("komelia-appearance-upgrade").toString()
            val url = "jdbc:sqlite:$directory/komelia.sqlite"
            Flyway.configure().dataSource(url, null, null).resourceProvider(AppMigrations())
                .javaMigrationClassProvider(AppMigrations()).target(version).load().migrate()
            val historicalChecksums = checksums(url)
            val previous = AppSettings(
                username = "audit@example.invalid", serverUrl = "https://catalog.invalid",
                cardWidth = 150, appTheme = AppTheme.DARKER, appLanguage = AppLanguage.ZH_CN,
                lanServerUrl = "https://lan.invalid", lanAutoSwitchEnabled = true,
            )
            saveVersion17AppSettings(url, previous)
            val migrated = ExposedSettingsRepository(KomeliaDatabase(directory).app).get()
            assertEquals(previous, migrated)
            assertEquals(historicalChecksums, checksums(url).filterKeys { it.toInt() <= version.toInt() })
            assertEquals(previous, ExposedSettingsRepository(KomeliaDatabase(directory).app).get())
        }
    }

    @Test
    fun everyThemeSurfaceAndTransparencyCombinationSurvivesReopening() = runBlocking {
        val directory = createTempDirectory("komelia-appearance-roundtrip").toString()
        val repository = ExposedSettingsRepository(KomeliaDatabase(directory).app)
        for (theme in AppTheme.entries) {
            for (surface in AppSurfaceStyle.entries) {
                for (reduceTransparency in listOf(false, true)) {
                    val settings = AppSettings(appTheme = theme, surfaceStyle = surface, reduceTransparency = reduceTransparency)
                    repository.save(settings)
                    assertEquals(settings, ExposedSettingsRepository(KomeliaDatabase(directory).app).get())
                }
            }
        }
    }

    @Test
    fun oldBrowserJsonAndUnknownFutureStylesUseClassicWithoutChangingTheTheme() {
        val legacy = Json.decodeFromString<AppSettings>("""{"appTheme":"DARKER","cardWidth":150}""")
        assertEquals(AppSurfaceStyle.CLASSIC, legacy.surfaceStyle)
        assertEquals(AppTheme.DARKER, legacy.appTheme)
        assertEquals(150, legacy.cardWidth)
        assertFalse(legacy.reduceTransparency)
        val future = Json.decodeFromString<AppSettings>("""{"surfaceStyle":"FUTURE_STYLE","appTheme":"LIGHT"}""")
        assertEquals(AppSurfaceStyle.CLASSIC, future.surfaceStyle)
        assertEquals(AppTheme.LIGHT, future.appTheme)
    }

    private fun checksums(url: String): Map<String, Int?> = DriverManager.getConnection(url).use { connection ->
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
