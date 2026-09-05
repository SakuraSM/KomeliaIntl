package snd.komelia.ui.reader.epub

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import snd.komelia.settings.model.TtsuReaderSettings

internal fun komgaReaderBackground(settings: JsonObject): Color =
    when (settings["appearance"]?.jsonPrimitive?.content) {
        "readium-sepia-on" -> Color(0xfffaf4e8)
        "readium-night-on" -> Color.Black
        else -> Color.White
    }

internal fun ttsuReaderBackground(settings: TtsuReaderSettings): Color {
    val custom = settings.customThemes[settings.theme]?.backgroundColor
    if (custom != null) parseReaderCssColor(custom)?.let { return it }
    return when (settings.theme) {
        "ecru-theme" -> Color(0xfff7f6eb)
        "water-theme" -> Color(0xffdfecf4)
        "gray-theme" -> Color(0xff23272a)
        "dark-theme" -> Color(0xff121212)
        "black-theme" -> Color.Black
        else -> Color.White
    }
}

/** Custom TTU colors are serialized as rgb/rgba expressions. */
internal fun parseReaderCssColor(value: String): Color? = runCatching {
    val channels = value.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
    require(value.trim().startsWith("rgb") && channels.size in 3..4)
    Color(
        red = (channels[0] / 255f).coerceIn(0f, 1f),
        green = (channels[1] / 255f).coerceIn(0f, 1f),
        blue = (channels[2] / 255f).coerceIn(0f, 1f),
        alpha = channels.getOrElse(3) { 1f }.coerceIn(0f, 1f),
    )
}.getOrNull()
