package snd.komelia.db.settings

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import snd.komelia.db.EpubReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.settings.model.EpubDisplaySettings
import snd.komelia.settings.model.EpubReaderType
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class EpubDisplaySettingsIntegrationTest {
    @Test fun migrationKeepsExistingReaderSettingsAndAddsSafeDefaults() = runBlocking {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { sql ->
                sql.execute("CREATE TABLE EpubReaderSettings(book_id TEXT PRIMARY KEY, reader_type TEXT NOT NULL, komga_settings_json TEXT NOT NULL, ttsu_settings_json TEXT NOT NULL)")
                sql.execute("INSERT INTO EpubReaderSettings VALUES ('default', 'KOMGA_EPUB', '{\"appearance\":\"readium-sepia-on\"}', '{}')")
                val migration = requireNotNull(AppMigrations().getResource("V15__epub_display_settings.sql"))
                sql.execute(migration.read().use { it.readText() })
                sql.executeQuery("SELECT * FROM EpubReaderSettings").use { rows ->
                    check(rows.next())
                    assertEquals("KOMGA_EPUB", rows.getString("reader_type"))
                    assertEquals("{\"appearance\":\"readium-sepia-on\"}", rows.getString("komga_settings_json"))
                    assertEquals(EpubDisplaySettings(), Json.decodeFromString<EpubDisplaySettings>(rows.getString("display_settings_json")))
                }
            }
        }
    }

    @Test fun displayPreferencesSurviveDatabaseReopen() = runBlocking {
        val directory = createTempDirectory("komelia-display-test").toString()
        val settings = EpubReaderSettings(
            readerType = EpubReaderType.KOMGA_EPUB,
            displaySettings = EpubDisplaySettings(immersiveMode = true, extraTopSpacingDp = 24),
            komgaReaderSettings = JsonObject(mapOf("appearance" to JsonPrimitive("readium-sepia-on"))),
        )
        ExposedEpubReaderSettingsRepository(KomeliaDatabase(directory).app).save(settings)
        assertEquals(settings, ExposedEpubReaderSettingsRepository(KomeliaDatabase(directory).app).get())
    }
}
