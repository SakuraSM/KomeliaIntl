package snd.komelia.db.settings

import kotlinx.coroutines.runBlocking
import snd.komelia.db.ImageReaderSettings
import snd.komelia.db.KomeliaDatabase
import snd.komelia.image.UpsamplingMode
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageUpsamplingSettingsIntegrationTest {
    @Test fun retainsExistingAndNewUpsamplingValuesAcrossDatabaseReopen() = runBlocking {
        val directory = createTempDirectory("komelia-upsampling-settings-test").toString()
        val repository = ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app)
        for (mode in UpsamplingMode.entries) {
            val settings = ImageReaderSettings(upsamplingMode = mode, cropBorders = true)
            repository.save(settings)
            assertEquals(settings, ExposedImageReaderSettingsRepository(KomeliaDatabase(directory).app).get())
        }
    }
}
