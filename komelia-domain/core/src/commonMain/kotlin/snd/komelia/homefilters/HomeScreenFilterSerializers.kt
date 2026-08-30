package snd.komelia.homefilters

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Serialization support for generic Komga string equality operators.
 *
 * `KomgaSearchOperator.Is<String>` is also a non-generic `StringOp`, so its generated
 * serializer loses the concrete type argument and falls back to polymorphic `Any`.
 * JSON primitives cannot carry a class discriminator; boxing keeps object-style
 * polymorphism and therefore preserves the existing home-filter JSON format.
 */
val HomeScreenFilterSerializersModule = SerializersModule {
    polymorphic(Any::class) {
        subclass(String::class, BoxedStringSerializer)
    }
}

/** Network requests only encode these values, and Komga expects a JSON string. */
val KomgaSearchRequestSerializersModule = SerializersModule {
    polymorphic(Any::class) {
        subclass(String::class, RawStringSerializer)
    }
}

private object BoxedStringSerializer : KSerializer<String> {
    override val descriptor = buildClassSerialDescriptor("kotlin.String") {
        element<String>("value")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value)
        }
    }

    override fun deserialize(decoder: Decoder): String {
        var value: String? = null
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> value = decodeStringElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        return requireNotNull(value)
    }
}

private object RawStringSerializer : KSerializer<String> {
    // A class descriptor satisfies JSON polymorphic validation while the payload stays primitive.
    override val descriptor = buildClassSerialDescriptor("kotlin.String")

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String = decoder.decodeString()
}
