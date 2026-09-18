package snd.komelia.db.settings

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.settings.model.ReaderType
import snd.komelia.settings.model.PagedReadingDirection
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PanelPrerenderSettingsIntegrationTest {
    @Test fun migratesExistingVersion15PreferencesWithoutResettingThem() = runBlocking {
        val directory = createTempDirectory("komelia-panel-migration-test").toString()
        val url = "jdbc:sqlite:$directory/komelia.sqlite"
        Flyway.configure().dataSource(url, null, null).resourceProvider(AppMigrations())
            .javaMigrationClassProvider(AppMigrations()).target("15").load().migrate()
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("""
                    INSERT INTO ImageReaderSettings
                    (book_id, reader_type, stretch_to_fit, paged_scale_type, paged_reading_direction,
                     paged_page_layout, continuous_reading_direction, continuous_padding, continuous_page_spacing, crop_borders)
                    VALUES ('DEFAULT', 'PANELS', 0, 'SCREEN', 'RIGHT_TO_LEFT', 'SINGLE_PAGE', 'TOP_TO_BOTTOM', 0.2, 10, 1)
                """.trimIndent())
            }
        }
        val migrated = requireNotNull(ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get())
        assertEquals(1, migrated.panelPrerenderCount)
        assertEquals(ReaderType.PANELS, migrated.readerType)
        assertEquals(PagedReadingDirection.RIGHT_TO_LEFT, migrated.pagedReadingDirection)
        assertEquals(0.2f, migrated.continuousPadding)
        assertTrue(migrated.cropBorders)
    }

    @Test fun olderSettingsWithoutNewFieldDefaultToOne() {
        val settings = Json.decodeFromString<ImageReaderSettings>("""{"cropBorders":true}""")
        assertEquals(1, settings.panelPrerenderCount)
        assertTrue(settings.cropBorders)
    }

    @Test fun allSupportedCountsSurviveDatabaseReopenWithoutChangingOtherPreferences() = runBlocking {
        val directory = createTempDirectory("komelia-panel-settings-test").toString()
        val repository = ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app)
        for (count in 0..2) {
            val settings = ImageReaderSettings(panelPrerenderCount = count, cropBorders = true)
            repository.save(settings)
            assertEquals(settings, ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get())
        }
    }
}
