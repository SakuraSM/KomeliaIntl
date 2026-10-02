package snd.komelia.db.color

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import snd.komelia.color.*
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.db.settings.ExposedImageReaderSettingsRepository
import snd.komga.client.book.KomgaBookId
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class DefaultColorCorrectionIntegrationTest {
    @Test fun migrationPreservesLegacyBookOverridesAndLeavesGlobalDisabled() = runBlocking {
        val directory = createTempDirectory("komelia-color-v16").toString()
        val url = "jdbc:sqlite:$directory/komelia.sqlite"
        Flyway.configure().dataSource(url, null, null).resourceProvider(AppMigrations())
            .javaMigrationClassProvider(AppMigrations()).target("16").load().migrate()
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use {
                it.executeUpdate("INSERT INTO BookColorCorrection(book_id,type) VALUES ('legacy','COLOR_LEVELS')")
            }
        }
        val database = KomeliaDatabase(directory).app
        val repository = ExposedBookColorCorrectionRepository(database)
        assertEquals(BookColorCorrectionMode.CUSTOM, repository.getMode(KomgaBookId("legacy")).first())
        assertEquals(ColorCorrectionType.COLOR_LEVELS, repository.getCurrentType(KomgaBookId("legacy")).first())
        assertEquals(BookColorCorrectionMode.INHERIT, repository.getMode(KomgaBookId("new")).first())
        assertNull(ExposedImageReaderSettingsRepository(database).get()?.defaultColorCorrection)
    }

    @Test fun bothSnapshotTypesSurviveReopenAndPresetDeletion() = runBlocking {
        val directory = createTempDirectory("komelia-default-color").toString()
        val database = KomeliaDatabase(directory).app
        val settings = ExposedImageReaderSettingsRepository(database)
        val preset = ColorLevelsPreset("Gamma", ColorLevelChannels.DEFAULT.copy(color = ColorLevelsConfig.DEFAULT.copy(gamma = 2f)))
        val presets = ExposedColorLevelsPresetRepository(database)
        presets.savePreset(preset)
        val values = listOf(
            DefaultColorCorrection(preset.name, ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS, levels = preset.channels)),
            DefaultColorCorrection("Curve", ColorCorrectionConfig(ColorCorrectionType.COLOR_CURVES,
                curves = ColorCurvePoints.DEFAULT.copy(colorCurvePoints = listOf(CurvePoint(0f, 1f, CurvePointType.CORNER), CurvePoint(1f, 0f, CurvePointType.CORNER))))),
        )
        for (value in values) {
            val expected = ImageReaderSettings(defaultColorCorrection = value, cropBorders = true)
            settings.save(expected)
            presets.deletePreset(preset)
            assertEquals(expected, ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get())
        }
        settings.save(ImageReaderSettings())
        assertNull(ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get()?.defaultColorCorrection)
    }

    @Test fun bookModeSurvivesReopenWithoutDiscardingCustomValues() = runBlocking {
        val directory = createTempDirectory("komelia-book-color").toString()
        val repository = ExposedBookColorCorrectionRepository(KomeliaDatabase(directory).app)
        val book = KomgaBookId("one")
        val levels = BookColorLevels(book, ColorLevelChannels.DEFAULT.copy(color = ColorLevelsConfig.DEFAULT.copy(gamma = 2f)))
        repository.saveConfiguration(book, ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS, levels = levels.channels))
        for (mode in BookColorCorrectionMode.entries) {
            repository.setMode(book, mode)
            val reopened = ExposedBookColorCorrectionRepository(KomeliaDatabase(directory).app)
            assertEquals(mode, reopened.getMode(book).first())
            assertEquals(levels, reopened.getLevels(book).first())
            assertEquals(BookColorCorrectionMode.INHERIT, reopened.getMode(KomgaBookId("other")).first())
        }
    }

    @Test fun olderBrowserSettingsDefaultToDisabled() {
        val settings = Json.decodeFromString<ImageReaderSettings>("""{"cropBorders":true}""")
        assertNull(settings.defaultColorCorrection)
        val encoded = Json.encodeToString(settings)
        assertEquals(settings, Json.decodeFromString<ImageReaderSettings>(encoded))
    }

    @Test fun failedCustomSaveRollsBackModeTypeAndParametersTogether() = runBlocking {
        val directory = createTempDirectory("komelia-color-rollback").toString()
        val repository = ExposedBookColorCorrectionRepository(KomeliaDatabase(directory).app)
        val book = KomgaBookId("existing")
        repository.saveConfiguration(book, ColorCorrectionConfig(ColorCorrectionType.COLOR_CURVES))
        repository.setMode(book, BookColorCorrectionMode.DISABLED)
        DriverManager.getConnection("jdbc:sqlite:$directory/komelia.sqlite").use { connection ->
            connection.createStatement().use {
                it.executeUpdate("CREATE TRIGGER reject_color_save BEFORE INSERT ON BookColorLevels BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            }
        }
        assertFailsWith<Exception> {
            repository.saveConfiguration(book, ColorCorrectionConfig(ColorCorrectionType.COLOR_LEVELS))
        }
        assertEquals(BookColorCorrectionMode.DISABLED, repository.getMode(book).first())
        assertEquals(ColorCorrectionType.COLOR_CURVES, repository.getCurrentType(book).first())
        assertEquals(ColorCurvePoints.DEFAULT, repository.getCurve(book).first()?.channels)
    }
}
