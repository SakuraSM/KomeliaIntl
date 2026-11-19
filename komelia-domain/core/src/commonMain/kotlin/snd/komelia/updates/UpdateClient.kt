package snd.komelia.updates

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import io.ktor.utils.io.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.io.readByteArray
import kotlin.time.Instant

private const val onnxRuntimeBaseUrl = "https://api.github.com/repos/microsoft/onnxruntime"

class UpdateClient(
    private val ktor: HttpClient,
    private val ktorWithoutCache: HttpClient,
    private val appReleasesApiUrl: String = AppProjectMetadata.releasesApiUrl,
) {

    suspend fun getKomeliaReleases(): List<GithubRelease> {
        return getReleases(appReleasesApiUrl)
    }

    suspend fun getReleases(apiUrl: String, limit: Int = 5): List<GithubRelease> {
        return ktor.get(apiUrl) {
            parameter("per_page", limit)
        }.body()
    }

    suspend fun getKomeliaLatestRelease(): GithubRelease {
        return ktor.get(appReleaseEndpoint(appReleasesApiUrl, "latest")).body()
    }

    suspend fun getOnnxRuntimeRelease(tagName: String): GithubRelease {
        return ktor.get("$onnxRuntimeBaseUrl/releases/tags/$tagName").body()
    }

    suspend fun streamFile(url: String, block: suspend (response: HttpResponse) -> Unit) {
        ktorWithoutCache.prepareGet(url).execute(block)
    }

    internal suspend fun getAndroidAbiManifest(url: String): AndroidAbiApks? = withTimeoutOrNull(5_000) {
        ktorWithoutCache.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) return@execute null
            val bytes = response.bodyAsChannel().readRemaining(65_537).readByteArray()
            if (bytes.size > 65_536) return@execute null
            Json.decodeFromString<AndroidAbiApks>(bytes.decodeToString())
        }
    }
}

internal fun appReleaseEndpoint(baseUrl: String, path: String): String =
    "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

@Serializable
data class GithubRelease(
    val id: Int,
    @SerialName("published_at")
    val publishedAt: Instant,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("html_url")
    val htmlUrl: String,
    val body: String,
    val assets: List<GithubReleaseAsset>
)

@Serializable
data class GithubReleaseAsset(
    val id: Int,
    val name: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String
)
