package snd.komelia.settings.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

const val DEFAULT_MOBILE_CARD_WIDTH = 240

@Serializable(with = AppSurfaceStyleSerializer::class)
enum class AppSurfaceStyle {
    CLASSIC,
    LIQUID_GLASS;

    companion object {
        fun fromStoredValue(value: String): AppSurfaceStyle =
            entries.firstOrNull { it.name == value } ?: CLASSIC
    }
}

object AppSurfaceStyleSerializer : KSerializer<AppSurfaceStyle> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AppSurfaceStyle", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: AppSurfaceStyle) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): AppSurfaceStyle = AppSurfaceStyle.fromStoredValue(decoder.decodeString())
}
