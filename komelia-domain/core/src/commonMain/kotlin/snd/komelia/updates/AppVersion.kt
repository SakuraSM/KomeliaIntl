package snd.komelia.updates

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Instant

@Serializable(with = AppVersionSerializer::class)
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<AppVersion> {

    companion object {
        // Keep in sync with gradle/libs.versions.toml app-version.
        val current = AppVersion(0, 18, 15)

        fun fromString(value: String): AppVersion {
            val match = VERSION_PATTERN.matchEntire(value.trim())
                ?: error("Can't parse version number: $value")
            return AppVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].ifEmpty { "0" }.toInt(),
            )
        }

        private val VERSION_PATTERN = Regex("^[vV]?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$")
    }

    override fun compareTo(other: AppVersion): Int {
        return compareBy(
            AppVersion::major,
            AppVersion::minor,
            AppVersion::patch
        ).compare(this, other)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }
}

data class AppRelease(
    val version: AppVersion,
    val publishDate: Instant,
    val releaseNotesBody: String,
    val htmlUrl: String,

    val assetName: String?,
    val assetUrl: String?,
)

object AppVersionSerializer : KSerializer<AppVersion> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AppVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AppVersion {
        val version = decoder.decodeString()
        return AppVersion.fromString(version)
    }

    override fun serialize(encoder: Encoder, value: AppVersion) {
        encoder.encodeString(value.toString())
    }
}
