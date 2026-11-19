package snd.komelia.updates

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable
internal data class AndroidAbiApks(
    val schemaVersion: Int,
    val version: String,
    val packages: Map<String, AndroidAbiApk>,
)

@Serializable
internal data class AndroidAbiApk(val name: String, val url: String, val sha256: String)

internal data class AndroidApkSelection(val name: String, val url: String, val sha256: String? = null)

internal const val ANDROID_ABI_MANIFEST = "android-abi-apks.json"
private val androidAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
private val sha256Pattern = Regex("[a-f0-9]{64}")

internal fun selectAndroidApk(
    version: AppVersion,
    supportedAbis: List<String>,
    manifest: AndroidAbiApks?,
    releaseAssets: List<GithubReleaseAsset>,
): AndroidApkSelection? {
    if (manifest?.schemaVersion == 1 && manifest.version == version.toString()) {
        for (abi in supportedAbis) {
            if (abi !in androidAbis) continue
            val apk = manifest.packages[abi] ?: continue
            val expectedName = "Komelia-$version-$abi.apk"
            if (apk.name == expectedName && sha256Pattern.matches(apk.sha256) && isSafeApkUrl(apk.url, expectedName)) {
                return AndroidApkSelection(apk.name, apk.url, apk.sha256)
            }
        }
    }
    val apks = releaseAssets.filter { it.name.endsWith(".apk") }
    val universal = apks.singleOrNull { it.name == "Komelia-$version.apk" }
        ?: apks.singleOrNull()?.takeUnless { apk -> androidAbis.any { apk.name.endsWith("-$it.apk") } }
    return universal?.let { AndroidApkSelection(it.name, it.browserDownloadUrl) }
}

private fun isSafeApkUrl(value: String, name: String): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() && url.user == null && url.password == null &&
        url.encodedPath.endsWith("/$name") && url.fragment.isEmpty()
}
