package snd.komelia.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidAbiApksTest {
    private val version = AppVersion(0, 33, 0, "beta.1")
    private val hash = "a".repeat(64)
    private val universal = GithubReleaseAsset(1, "Komelia-0.33.0-beta.1.apk", "", "https://github.com/universal.apk")
    private val arm64 = AndroidAbiApk(
        "Komelia-0.33.0-beta.1-arm64-v8a.apk",
        "https://downloads.example.test/Komelia-0.33.0-beta.1-arm64-v8a.apk",
        hash,
    )
    private val x86 = AndroidAbiApk(
        "Komelia-0.33.0-beta.1-x86_64.apk",
        "https://downloads.example.test/Komelia-0.33.0-beta.1-x86_64.apk",
        "b".repeat(64),
    )
    private val manifest = AndroidAbiApks(1, "0.33.0-beta.1", mapOf("arm64-v8a" to arm64, "x86_64" to x86))

    @Test fun selectsFirstSupportedArchitectureWithChecksum() {
        val selected = selectAndroidApk(version, listOf("x86_64", "arm64-v8a"), manifest, listOf(universal))
        assertEquals(x86.name, selected?.name)
        assertEquals(x86.url, selected?.url)
        assertEquals(x86.sha256, selected?.sha256)
    }

    @Test fun fallsBackToUniversalForMissingOrInvalidManifest() {
        val invalid = listOf(
            null,
            manifest.copy(schemaVersion = 2),
            manifest.copy(version = "0.32.0"),
            manifest.copy(packages = mapOf("arm64-v8a" to arm64.copy(sha256 = "bad"))),
            manifest.copy(packages = mapOf("arm64-v8a" to arm64.copy(name = "other.apk"))),
            manifest.copy(packages = mapOf("arm64-v8a" to arm64.copy(url = "http://downloads.example.test/${arm64.name}"))),
            manifest.copy(packages = mapOf("arm64-v8a" to arm64.copy(url = "https://user:pass@downloads.example.test/${arm64.name}"))),
        )
        for (value in invalid) {
            assertEquals(universal.browserDownloadUrl, selectAndroidApk(version, listOf("arm64-v8a"), value, listOf(universal))?.url)
        }
        assertEquals(universal.browserDownloadUrl, selectAndroidApk(version, listOf("unknown"), manifest, listOf(universal))?.url)
    }

    @Test fun supportsAnOldSingleApkWithoutManifest() {
        val old = GithubReleaseAsset(2, "legacy.apk", "", "https://github.com/legacy.apk")
        assertEquals(old.browserDownloadUrl, selectAndroidApk(version, listOf("arm64-v8a"), null, listOf(old))?.url)
    }

    @Test fun doesNotGuessWhenMultipleApksLackUniversal() {
        val first = GithubReleaseAsset(2, "other-arm64-v8a.apk", "", "https://github.com/a.apk")
        val second = GithubReleaseAsset(3, "other-x86.apk", "", "https://github.com/b.apk")
        assertNull(selectAndroidApk(version, listOf("arm64-v8a"), null, listOf(first, second)))
        assertNull(selectAndroidApk(version, listOf("x86"), null, listOf(first)))
    }
}
