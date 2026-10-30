package snd.komelia.db.settings

import java.sql.DriverManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.db.SettingsStateWrapper
import snd.komelia.db.migrations.AppMigrations
import snd.komelia.db.repository.ReaderSettingsRepositoryWrapper
import snd.komelia.settings.model.PagedReadingDirection
import snd.komelia.settings.model.ReaderType
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderModeHintSettingsIntegrationTest {
    @Test fun version19UpgradePreservesSettingsAndPublishedMigrationChecksums() = runBlocking {
        val directory = createTempDirectory("komelia-reader-hints-migration").toString()
        val url = "jdbc:sqlite:$directory/komelia.sqlite"
        Flyway.configure().dataSource(url, null, null).resourceProvider(AppMigrations())
            .javaMigrationClassProvider(AppMigrations()).target("19").load().migrate()
        val checksums = migrationChecksums(url)
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("""
                    INSERT INTO ImageReaderSettings
                    (book_id, reader_type, stretch_to_fit, paged_scale_type, paged_reading_direction,
                     paged_page_layout, continuous_reading_direction, continuous_padding,
                     continuous_page_spacing, crop_borders, panel_prerender_count, volume_keys_navigation)
                    VALUES ('DEFAULT', 'PANELS', 0, 'SCREEN', 'RIGHT_TO_LEFT', 'SINGLE_PAGE',
                            'TOP_TO_BOTTOM', 0.2, 10, 1, 2, 1)
                """.trimIndent())
            }
        }
        val migrated = requireNotNull(ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get())
        assertTrue(migrated.readerModeHintsEnabled)
        assertNull(migrated.lastReaderModeHintSignature)
        assertEquals(ReaderType.PANELS, migrated.readerType)
        assertEquals(PagedReadingDirection.RIGHT_TO_LEFT, migrated.pagedReadingDirection)
        assertEquals(0.2f, migrated.continuousPadding)
        assertEquals(2, migrated.panelPrerenderCount)
        assertTrue(migrated.cropBorders)
        assertTrue(migrated.volumeKeysNavigation)
        assertEquals(checksums, migrationChecksums(url).filterKeys { it.toInt() <= 19 })
    }

    @Test fun signaturesAndDisabledPreferenceSurviveRepositoryAndDatabaseReopening() = runBlocking {
        val directory = createTempDirectory("komelia-reader-hints-reopen").toString()
        val initial = ImageReaderSettings(cropBorders = true, volumeKeysNavigation = true)
        ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).save(initial)
        suspend fun reopen(): ReaderSettingsRepositoryWrapper {
            val storage = ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app)
            return ReaderSettingsRepositoryWrapper(SettingsStateWrapper(requireNotNull(storage.get()), storage::save))
        }
        assertTrue(reopen().consumeReaderModeHint(PAGED_LTR))
        assertFalse(reopen().consumeReaderModeHint(PAGED_LTR))
        assertTrue(reopen().consumeReaderModeHint(PAGED_RTL))
        reopen().putReaderModeHintsEnabled(false)
        assertFalse(reopen().getReaderModeHintsEnabled().first())
        assertFalse(reopen().consumeReaderModeHint(PAGED_LTR))
        reopen().putReaderModeHintsEnabled(true)
        assertTrue(reopen().consumeReaderModeHint(PAGED_RTL))
        assertFalse(reopen().consumeReaderModeHint(PAGED_RTL))
        val saved = requireNotNull(ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get())
        assertEquals(initial, saved.copy(readerModeHintsEnabled = true, lastReaderModeHintSignature = null))
    }

    @Test fun simultaneousEntriesClaimTheSameConfigurationOnlyOnce() = runBlocking {
        val releaseSave = CompletableDeferred<Unit>()
        val wrapper = SettingsStateWrapper(ImageReaderSettings()) { releaseSave.await() }
        val repository = ReaderSettingsRepositoryWrapper(wrapper)
        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.consumeReaderModeHint(PAGED_LTR) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.consumeReaderModeHint(PAGED_LTR) }
        releaseSave.complete(Unit)
        assertEquals(1, listOf(first.await(), second.await()).count { it })
    }

    @Test fun queuedHintCannotOverwriteADisabledPreference() = runBlocking {
        val releaseSave = CompletableDeferred<Unit>()
        val wrapper = SettingsStateWrapper(ImageReaderSettings()) { releaseSave.await() }
        val repository = ReaderSettingsRepositoryWrapper(wrapper)
        val disable = async(start = CoroutineStart.UNDISPATCHED) { repository.putReaderModeHintsEnabled(false) }
        val hint = async(start = CoroutineStart.UNDISPATCHED) { repository.consumeReaderModeHint(PAGED_LTR) }
        releaseSave.complete(Unit)
        disable.await()
        assertFalse(hint.await())
        assertFalse(wrapper.state.value.readerModeHintsEnabled)
        assertNull(wrapper.state.value.lastReaderModeHintSignature)
    }

    @Test fun failedWritesDoNotAcknowledgeEitherSeenOrDisabledState() = runBlocking {
        val initial = ImageReaderSettings()
        val wrapper = SettingsStateWrapper(initial) { throw java.io.IOException("Synthetic save failure") }
        val repository = ReaderSettingsRepositoryWrapper(wrapper)
        assertFailsWith<java.io.IOException> { repository.consumeReaderModeHint(PAGED_LTR) }
        assertFailsWith<java.io.IOException> { repository.putReaderModeHintsEnabled(false) }
        assertEquals(initial, wrapper.state.value)
    }

    private fun migrationChecksums(url: String): Map<String, Int> = DriverManager.getConnection(url).use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT version, checksum FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL").use { rows ->
                buildMap { while (rows.next()) put(rows.getString("version"), rows.getInt("checksum")) }
            }
        }
    }

    private companion object {
        const val PAGED_LTR = "PAGED:LEFT_TO_RIGHT"
        const val PAGED_RTL = "PAGED:RIGHT_TO_LEFT"
    }
}
