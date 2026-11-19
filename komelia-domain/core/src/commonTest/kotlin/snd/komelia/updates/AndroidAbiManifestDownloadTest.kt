package snd.komelia.updates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidAbiManifestDownloadTest {
    @Test fun readsValidManifestAndRejectsOversizedOrFailedResponses() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/ok" -> respond("""{"schemaVersion":1,"version":"0.33.0","packages":{}}""")
                        "/large" -> respond("x".repeat(65_537))
                        "/slow" -> { delay(10_000); respond("late") }
                        else -> respond("missing", status = HttpStatusCode.NotFound)
                    }
                }
            }
        }
        try {
            val updater = UpdateClient(client, client)
            assertEquals("0.33.0", updater.getAndroidAbiManifest("https://example.test/ok")?.version)
            assertNull(updater.getAndroidAbiManifest("https://example.test/large"))
            assertNull(updater.getAndroidAbiManifest("https://example.test/slow"))
            assertNull(updater.getAndroidAbiManifest("https://example.test/missing"))
        } finally {
            client.close()
        }
    }

}
