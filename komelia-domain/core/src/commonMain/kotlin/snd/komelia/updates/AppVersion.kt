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
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<AppVersion> {

    companion object {
        // Keep in sync with gradle/libs.versions.toml app-version.
        val current = AppVersion(0, 20, 0)

        fun fromString(value: String): AppVersion {
            val match = VERSION_PATTERN.matchEntire(value.trim())
                ?: error("Can't parse version number: $value")
            return AppVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].ifEmpty { "0" }.toInt(),
                preRelease = match.groupValues[4].ifEmpty { null },
            )
        }

        private const val PRE_RELEASE_IDENTIFIER =
            "(?:0|[1-9]\\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)"
        private val VERSION_PATTERN = Regex(
            "^[vV]?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?" +
                "(?:-($PRE_RELEASE_IDENTIFIER(?:\\.$PRE_RELEASE_IDENTIFIER)*))?" +
                "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
        )
    }

    override fun compareTo(other: AppVersion): Int {
        val stableVersionComparison = compareBy(
            AppVersion::major,
            AppVersion::minor,
            AppVersion::patch
        ).compare(this, other)
        if (stableVersionComparison != 0) return stableVersionComparison

        val left = preRelease ?: return if (other.preRelease == null) 0 else 1
        val right = other.preRelease ?: return -1
        val leftIdentifiers = left.split('.')
        val rightIdentifiers = right.split('.')

        leftIdentifiers.zip(rightIdentifiers).forEach { (leftIdentifier, rightIdentifier) ->
            val identifierComparison = comparePreReleaseIdentifier(leftIdentifier, rightIdentifier)
            if (identifierComparison != 0) return identifierComparison
        }
        return leftIdentifiers.size.compareTo(rightIdentifiers.size)
    }

    override fun toString(): String {
        return buildString {
            append("$major.$minor.$patch")
            preRelease?.let {
                append('-')
                append(it)
            }
        }
    }

    private fun comparePreReleaseIdentifier(left: String, right: String): Int {
        val leftIsNumeric = left.all(Char::isDigit)
        val rightIsNumeric = right.all(Char::isDigit)
        return when {
            leftIsNumeric && rightIsNumeric -> {
                left.length.compareTo(right.length).takeIf { it != 0 }
                    ?: left.compareTo(right)
            }

            leftIsNumeric -> -1
            rightIsNumeric -> 1
            else -> left.compareTo(right)
        }
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
