package snd.komelia

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import snd.komelia.updates.AndroidAppUpdater
import snd.komelia.updates.UpdateClient
import java.io.IOException

class AndroidAbiUpdateTest {
    @Test fun wrongChecksumAbandonsSessionAndAllowsRetry() = runBlocking { verifyFallback(false) }

    @Test fun missingAbiFallsBackWhenClientExpectsSuccess() = runBlocking { verifyFallback(true) }

    @Test fun unavailableOrInvalidManifestUsesUniversal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val release = """[{"id":1,"published_at":"2026-11-12T00:00:00Z","tag_name":"v0.33.0","html_url":"https://fixture.invalid/release","body":"","assets":[{"id":2,"name":"Komelia-0.33.0.apk","content_type":"application/vnd.android.package-archive","browser_download_url":"https://fixture.invalid/universal.apk"},{"id":3,"name":"android-abi-apks.json","content_type":"application/json","browser_download_url":"https://fixture.invalid/manifest.json"}]}]"""
        for (invalidJson in listOf(false, true)) {
            val client = HttpClient(MockEngine { request ->
                when (request.url.encodedPath) {
                    "/releases" -> respond(release, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    "/manifest.json" -> if (invalidJson) respond("invalid JSON") else respond("missing", status = HttpStatusCode.NotFound)
                    else -> respond("missing", status = HttpStatusCode.NotFound)
                }
            }) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            try {
                val selected = AndroidAppUpdater(UpdateClient(client, client, "https://fixture.invalid/releases"), context, true)
                    .getReleases().single()
                assertEquals("https://fixture.invalid/universal.apk", selected.assetUrl)
                assertNull(selected.assetSha256)
            } finally {
                client.close()
            }
        }
    }

    private suspend fun verifyFallback(abiMissing: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = context.packageManager.packageInstaller
        val existingSessions = installer.mySessions.map { it.sessionId }.toSet()
        val abi = Build.SUPPORTED_ABIS.first { it in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        val version = "0.33.0-beta.1"
        val name = "Komelia-$version-$abi.apk"
        val manifest = """{"schemaVersion":1,"version":"$version","packages":{"$abi":{"name":"$name","url":"https://fixture.invalid/$name","sha256":"${"0".repeat(64)}"}}}"""
        val release = """[{"id":1,"published_at":"2026-11-12T00:00:00Z","tag_name":"v$version","html_url":"https://fixture.invalid/release","body":"","assets":[{"id":2,"name":"Komelia-$version.apk","content_type":"application/vnd.android.package-archive","browser_download_url":"https://fixture.invalid/universal.apk"},{"id":3,"name":"android-abi-apks.json","content_type":"application/json","browser_download_url":"https://fixture.invalid/manifest.json"}]}]"""
        var abiAttempts = 0
        var universalAttempts = 0
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/releases" -> respond(release, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                "/manifest.json" -> respond(manifest, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                "/$name" -> {
                    abiAttempts++
                    if (abiMissing) respond("missing", status = HttpStatusCode.NotFound)
                    else respond("incorrect APK bytes", headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
                }
                "/universal.apk" -> { universalAttempts++; respond("missing", status = HttpStatusCode.NotFound) }
                else -> respond("missing", status = HttpStatusCode.NotFound)
            }
        }) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        try {
            val updater = AndroidAppUpdater(UpdateClient(client, client, "https://fixture.invalid/releases"), context, true)
            val selected = updater.getReleases().single()
            assertEquals(name, selected.assetName)
            assertEquals("https://fixture.invalid/universal.apk", selected.fallbackAssetUrl)
            repeat(2) {
                val flow = updater.updateTo(selected)
                assertNotNull(flow)
                val failure = runCatching { flow!!.collect() }.exceptionOrNull()
                assertTrue("The fallback's HTTP failure must be reported", failure is ResponseException && failure.response.status == HttpStatusCode.NotFound)
                assertTrue("The rejected ABI package must not be committed", failure!!.suppressed.any {
                    if (abiMissing) it is ResponseException && it.response.status == HttpStatusCode.NotFound
                    else it is IOException && it.message == "Downloaded APK checksum mismatch"
                })
            }
            assertEquals(2, abiAttempts)
            assertEquals(2, universalAttempts)
            val sessionsRemoved = withTimeoutOrNull(10_000) {
                while (installer.mySessions.any { it.sessionId !in existingSessions }) delay(50)
                true
            } ?: false
            assertTrue("Abandoned installer sessions must be removed", sessionsRemoved)
        } finally {
            client.close()
        }
    }
}
