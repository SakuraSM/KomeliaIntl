package snd.komelia.ui.reader.epub

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubChromeAppearanceTest {
    @Test fun oldAppearancePayloadsKeepTheStandardToolbar() {
        assertFalse(Json.decodeFromString<EpubChromeAppearance>("{}").isMacOS)
    }

    @Test fun macAppearanceIncludesTheOptionalHostFlag() {
        val json = Json.encodeToString(EpubChromeAppearance(isMacOS = true))
        assertTrue(Json.parseToJsonElement(json).jsonObject.getValue("isMacOS").jsonPrimitive.boolean)
    }
}
