package snd.komelia.ui.reader.epub

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import snd.komelia.settings.model.EpubDisplaySettings
import snd.komelia.settings.model.TtsuReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EpubDisplayTest {
    @Test fun oldSettingsDefaultToAutomaticCutoutAvoidance() {
        val settings = Json.decodeFromString<EpubDisplaySettings>("{}")
        assertFalse(settings.immersiveMode)
        assertEquals(8, settings.topSpacingDp)
        assertEquals(0, EpubDisplaySettings(extraTopSpacingDp = -10).topSpacingDp)
        assertEquals(48, EpubDisplaySettings(extraTopSpacingDp = Int.MAX_VALUE).topSpacingDp)
    }

    @Test fun komgaMarginsMatchAllReadingThemes() {
        fun background(appearance: String) = komgaReaderBackground(JsonObject(mapOf("appearance" to JsonPrimitive(appearance))))
        assertEquals(Color.White, background("readium-default-on"))
        assertEquals(Color(0xfffaf4e8), background("readium-sepia-on"))
        assertEquals(Color.Black, background("readium-night-on"))
        assertEquals(Color.White, komgaReaderBackground(JsonObject(emptyMap())))
    }

    @Test fun ttuMarginsMatchReadingThemesAndCustomRgbaColors() {
        assertEquals(Color(0xff121212), ttsuReaderBackground(TtsuReaderSettings(theme = "dark-theme")))
        assertEquals(Color(0xfff7f6eb), ttsuReaderBackground(TtsuReaderSettings(theme = "ecru-theme")))
        assertEquals(Color(0xff23272a), ttsuReaderBackground(TtsuReaderSettings(theme = "gray-theme")))
        assertEquals(Color(0xfffaf4e8), parseReaderCssColor("rgba(250, 244, 232, 1)"))
        assertNull(parseReaderCssColor("invalid"))
    }
}
